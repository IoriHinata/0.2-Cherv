package com.markovchapter.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class ImportedText(val name: String, val text: String)

class DocumentTextExtractor(private val context: Context) {
    fun read(uri: Uri): ImportedText {
        val name = queryName(context.contentResolver, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось открыть файл")
        val ext = name.substringAfterLast('.', "").lowercase()
        val text = when (ext) {
            "pdf" -> readPdf(bytes)
            "doc" -> readDoc(bytes)
            "docx" -> readZipXml(bytes) { it == "word/document.xml" || it.startsWith("word/header") || it.startsWith("word/footer") }
            "epub" -> readZipXml(bytes) { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") }
            "odt" -> readZipXml(bytes) { it == "content.xml" }
            "pptx" -> readZipXml(bytes) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            "xlsx" -> readZipXml(bytes) { it == "xl/sharedStrings.xml" || (it.startsWith("xl/worksheets/") && it.endsWith(".xml")) }
            "html", "htm" -> htmlToText(bytes.toString(Charsets.UTF_8))
            "rtf" -> rtfToText(bytes.toString(Charsets.UTF_8))
            "txt", "md", "markdown", "csv", "tsv", "xml", "json", "log", "text" ->
                decodeText(bytes)
            else -> error("Формат .$ext пока не поддерживается. Поддерживаются TXT, DOC, DOCX, PDF, EPUB, RTF, HTML, ODT, PPTX, XLSX, MD, CSV, XML и JSON.")
        }.trim()
        if (text.isBlank()) error("В файле не найден извлекаемый текст")
        return ImportedText(name, text)
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String {
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: (uri.lastPathSegment ?: "document.txt")
    }

    private fun readPdf(bytes: ByteArray): String {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
    }

    @Suppress("DEPRECATION")
    private fun readDoc(bytes: ByteArray): String {
        val document = HWPFDocument(ByteArrayInputStream(bytes))
        return document.use { doc -> WordExtractor(doc).use { it.text } }
    }

    private fun readZipXml(bytes: ByteArray, include: (String) -> Boolean): String {
        val out = StringBuilder()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && include(entry.name)) {
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    out.append(xmlToText(buffer.toString(Charsets.UTF_8))).append('\n')
                }
                zip.closeEntry()
            }
        }
        return out.toString()
    }

    private fun xmlToText(source: String): String =
        Html.fromHtml(source.replace(Regex("<(w:)?tab[^>]*/>"), " ")
            .replace(Regex("</(w:p|a:p|text:p|p)>"), "\n"), Html.FROM_HTML_MODE_LEGACY)
            .toString().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n")

    private fun htmlToText(source: String): String = xmlToText(
        source.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
    )

    private fun rtfToText(source: String): String = source
        .replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
        .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), " ")
        .replace(Regex("[{}]"), "")
        .replace(Regex("\\s+"), " ")

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else utf8
    }
}
