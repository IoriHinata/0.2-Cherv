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
            "docx", "docm", "dotx", "dotm" -> readZipXml(bytes) { it == "word/document.xml" || it.startsWith("word/header") || it.startsWith("word/footer") }
            "fb2" -> xmlToText(bytes.toString(Charsets.UTF_8))
            "epub" -> readEpub(bytes)
            "odt" -> readZipXml(bytes) { it == "content.xml" }
            "pptx" -> readZipXml(bytes) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            "xlsx" -> readZipXml(bytes) { it == "xl/sharedStrings.xml" || (it.startsWith("xl/worksheets/") && it.endsWith(".xml")) }
            "html", "htm" -> htmlToText(bytes.toString(Charsets.UTF_8))
            "rtf" -> rtfToText(bytes.toString(Charsets.UTF_8))
            "txt", "md", "markdown", "csv", "tsv", "xml", "json", "log", "text", "tex", "rst", "adoc", "yaml", "yml", "ini", "properties", "sql" ->
                decodeText(bytes)
            else -> error("Формат .$ext пока не поддерживается. Поддерживаются TXT, DOC, DOCX/DOCM, PDF, EPUB, FB2, RTF, HTML, ODT, PPTX, XLSX, MD, CSV, XML и JSON.")
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
                    out.append(xmlToText(String(buffer.toByteArray(), Charsets.UTF_8))).append('\n')
                }
                zip.closeEntry()
            }
        }
        return out.toString()
    }


    /**
     * EPUB is a ZIP container. Read chapters in OPF spine order rather than ZIP
     * entry order, which is not guaranteed to match the book's reading order.
     */
    private fun readEpub(bytes: ByteArray): String {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val data = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var entryBytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        require(entryBytes <= 32L * 1024 * 1024 && totalBytes <= 128L * 1024 * 1024) {
                            "EPUB слишком велик для безопасного импорта"
                        }
                        data.write(buffer, 0, count)
                    }
                    entries[entry.name.replace('\\', '/').removePrefix("./")] = data.toByteArray()
                }
                zip.closeEntry()
            }
        }
        require(entries.isNotEmpty()) { "EPUB-контейнер пуст или повреждён" }

        val orderedChapters = runCatching {
            val container = parseXml(entries["META-INF/container.xml"]
                ?: error("В EPUB отсутствует META-INF/container.xml"))
            val rootfile = container.getElementsByTagName("rootfile").item(0)
                ?: error("В EPUB не найден OPF-файл")
            val opfPath = rootfile.attributes.getNamedItem("full-path")?.nodeValue
                ?: error("В EPUB не указан путь к OPF-файлу")
            val opf = parseXml(entries[opfPath] ?: error("Не найден OPF-файл: $opfPath"))
            val manifest = linkedMapOf<String, String>()
            val items = opf.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val item = items.item(i)
                val id = item.attributes.getNamedItem("id")?.nodeValue ?: continue
                val href = item.attributes.getNamedItem("href")?.nodeValue ?: continue
                val media = item.attributes.getNamedItem("media-type")?.nodeValue.orEmpty()
                if (media.contains("xhtml") || media.contains("html")) {
                    val cleanHref = href.substringBefore('#').substringBefore('?')
                    val joined = opfPath.substringBeforeLast('/', "").let {
                        if (it.isEmpty()) cleanHref else "$it/$cleanHref"
                    }
                    val resolved = java.net.URI(null, null, joined, null).normalize().path
                    manifest[id] = java.net.URLDecoder.decode(resolved, "UTF-8")
                }
            }
            val spine = opf.getElementsByTagName("itemref")
            val paths = buildList {
                for (i in 0 until spine.length) {
                    val id = spine.item(i).attributes.getNamedItem("idref")?.nodeValue ?: continue
                    manifest[id]?.let { add(it) }
                }
            }
            paths.mapNotNull { path -> entries[path] ?: entries[path.replace("%20", " ")] }
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        val chapters = orderedChapters ?: entries.entries
            .filter { (name, _) ->
                val lower = name.lowercase()
                (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                    !lower.contains("nav") && !lower.contains("toc")
            }
            .sortedBy { it.key }
            .map { it.value }

        return chapters.joinToString("\n\n") { htmlToText(String(it, Charsets.UTF_8)) }
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun xmlToText(source: String): String =
        Html.fromHtml(source.replace(Regex("<(w:)?tab[^>]*/>"), " ")
            .replace(Regex("</(w:p|a:p|text:p|p)>"), "\n"), Html.FROM_HTML_MODE_LEGACY)
            .toString().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n")

    private fun htmlToText(source: String): String = xmlToText(
        source.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)</(p|div|section|article|h[1-6]|li|blockquote|tr)>"), "\\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\\n")
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
