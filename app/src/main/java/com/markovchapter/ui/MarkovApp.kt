package com.markovchapter.ui

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markovchapter.data.*

@Composable
fun MarkovApp(vm: MarkovViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::importDocument)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(s.output) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("MarkovChapter", style = MaterialTheme.typography.headlineMedium)
        TabRow(selectedTabIndex = tab) {
            listOf("Обучение", "Сущности", "Генерация").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.status)
                Text("Обработано слов: ${s.trainedWords}")
                if (s.training) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !s.training, modifier = Modifier.fillMaxWidth()) {
                    Text("Загрузить документ")
                }
                Text(
                    "Форматы: TXT, DOC, DOCX, PDF, EPUB, RTF, HTML, ODT, PPTX, XLSX, MD, CSV, XML, JSON",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = vm::clear, modifier = Modifier.fillMaxWidth()) { Text("Очистить модель") }
                Text("Файлы обрабатываются локально на устройстве.")
            }
            1 -> SettingsScreen(s.settings, vm::settings)
            else -> GenerationScreen(
                state = s,
                onCount = vm::count,
                onGenerate = vm::generate,
                onToggleModule = vm::toggleModule,
                onMoveModule = vm::moveModule,
                onCopy = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Глава", s.output))
                },
                onSave = { save.launch("chapter.txt") }
            )
        }
    }
}

@Composable
private fun SettingsScreen(s: AppSettings, change: (AppSettings) -> Unit) {
    var character by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var weight by remember { mutableIntStateOf(5) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Персонажи", style = MaterialTheme.typography.titleLarge)
        Row {
            OutlinedTextField(character, { character = it }, label = { Text("Имя") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (character.isNotBlank()) {
                    change(s.copy(characters = s.characters + WeightedEntity(character.trim(), weight)))
                    character = ""
                }
            }) { Text("+") }
        }
        Text("Важность: $weight")
        Slider(weight.toFloat(), { weight = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 8)
        s.characters.forEachIndexed { i, e ->
            Row {
                Text("${e.name} · ${e.importance}", Modifier.weight(1f))
                TextButton(onClick = { change(s.copy(characters = s.characters.filterIndexed { n, _ -> n != i })) }) { Text("Удалить") }
            }
        }
        HorizontalDivider()
        Text("Места", style = MaterialTheme.typography.titleLarge)
        Row {
            OutlinedTextField(place, { place = it }, label = { Text("Название места") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (place.isNotBlank()) {
                    change(s.copy(places = s.places + WeightedEntity(place.trim(), weight)))
                    place = ""
                }
            }) { Text("+") }
        }
        s.places.forEachIndexed { i, e ->
            Row {
                Text("${e.name} · ${e.importance}", Modifier.weight(1f))
                TextButton(onClick = { change(s.copy(places = s.places.filterIndexed { n, _ -> n != i })) }) { Text("Удалить") }
            }
        }
        HorizontalDivider()
        Text("Имена собственные", style = MaterialTheme.typography.titleLarge)
        Row {
            OutlinedTextField(name, { name = it }, label = { Text("Имя") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (name.isNotBlank()) {
                    change(s.copy(properNames = s.properNames + name.trim()))
                    name = ""
                }
            }) { Text("+") }
        }
        s.properNames.forEachIndexed { i, value ->
            Row {
                Text(value, Modifier.weight(1f))
                TextButton(onClick = { change(s.copy(properNames = s.properNames.filterIndexed { n, _ -> n != i })) }) { Text("Удалить") }
            }
        }
        HorizontalDivider()
        Text("Свои фразы", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(phrase, { phrase = it }, label = { Text("Реплика или предложение") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (phrase.isNotBlank()) {
                change(s.copy(phrases = s.phrases + WeightedPhrase(phrase.trim(), weight)))
                phrase = ""
            }
        }) { Text("Добавить фразу · вес $weight") }
        s.phrases.forEachIndexed { i, value ->
            Row {
                Text("${value.text} · вес ${value.weight}", Modifier.weight(1f))
                TextButton(onClick = { change(s.copy(phrases = s.phrases.filterIndexed { n, _ -> n != i })) }) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun GenerationScreen(
    state: UiState,
    onCount: (Int) -> Unit,
    onGenerate: () -> Unit,
    onToggleModule: (GenerationModule, Boolean) -> Unit,
    onMoveModule: (GenerationModule, Int) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Количество предложений: ${state.count}")
        Slider(state.count.toFloat(), { onCount(it.toInt()) }, valueRange = 5f..20f, steps = 14)
        Text("Модули главы", style = MaterialTheme.typography.titleLarge)
        Text("Отметьте нужные блоки. Порядок включённых блоков определяет порядок их появления в главе.", style = MaterialTheme.typography.bodySmall)
        GenerationModule.values().forEach { module ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = module in state.settings.modules, onCheckedChange = { onToggleModule(module, it) })
                Text(module.title)
            }
        }
        Text("Порядок модулей", style = MaterialTheme.typography.titleMedium)
        state.settings.modules.forEachIndexed { index, module ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${index + 1}. ${module.title}", Modifier.weight(1f))
                TextButton(onClick = { onMoveModule(module, -1) }, enabled = index > 0) { Text("↑") }
                TextButton(onClick = { onMoveModule(module, 1) }, enabled = index < state.settings.modules.lastIndex) { Text("↓") }
            }
        }
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) { Text("Сгенерировать главу") }
        OutlinedTextField(
            value = state.output,
            onValueChange = {},
            readOnly = true,
            label = { Text("Результат") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
        )
        Row {
            OutlinedButton(onClick = onCopy, enabled = state.output.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Копировать") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onSave, enabled = state.output.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Сохранить") }
        }
    }
}
