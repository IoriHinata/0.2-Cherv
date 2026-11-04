package com.markovchapter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.markovchapter.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val trainedWords: Int = 0,
    val training: Boolean = false,
    val status: String = "Загрузите текстовый документ",
    val settings: AppSettings = AppSettings(),
    val output: String = "",
    val count: Int = 10
)

class MarkovViewModel(app: Application) : AndroidViewModel(app) {
    private val chain = MarkovChain()
    private val store = SettingsStore(app)
    private val extractor = DocumentTextExtractor(app)
    private val _state = MutableStateFlow(UiState(settings = store.load()))
    val state = _state.asStateFlow()

    fun importDocument(uri: Uri) = viewModelScope.launch {
        _state.value = _state.value.copy(training = true, status = "Чтение документа…")
        runCatching {
            val document = withContext(Dispatchers.IO) { extractor.read(uri) }
            _state.value = _state.value.copy(status = "Обучение: ${document.name}")
            val words = withContext(Dispatchers.Default) {
                chain.train(document.text)
                chain.wordCount
            }
            _state.value = _state.value.copy(
                training = false,
                trainedWords = words,
                status = "Добавлен файл: ${document.name}"
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                training = false,
                status = "Ошибка импорта: ${error.message ?: "не удалось прочитать файл"}"
            )
        }
    }

    fun clear() {
        chain.clear()
        _state.value = _state.value.copy(trainedWords = 0, status = "Модель очищена", output = "")
    }

    fun settings(s: AppSettings) {
        store.save(s)
        _state.value = _state.value.copy(settings = s)
    }

    fun count(n: Int) {
        _state.value = _state.value.copy(count = n.coerceIn(5, 20))
    }

    fun toggleModule(module: GenerationModule, enabled: Boolean) {
        val current = _state.value.settings.modules.toMutableList()
        if (enabled && module !in current) current.add(module)
        if (!enabled) current.remove(module)
        settings(_state.value.settings.copy(modules = current))
    }

    fun moveModule(module: GenerationModule, delta: Int) {
        val modules = _state.value.settings.modules.toMutableList()
        val from = modules.indexOf(module)
        val to = from + delta
        if (from < 0 || to !in modules.indices) return
        val item = modules.removeAt(from)
        modules.add(to, item)
        settings(_state.value.settings.copy(modules = modules))
    }

    fun generate() {
        if (!chain.isTrained()) {
            _state.value = _state.value.copy(status = "Сначала обучите модель")
            return
        }
        val s = _state.value.settings
        val modules = s.modules
        if (modules.isEmpty()) {
            _state.value = _state.value.copy(status = "Выберите хотя бы один модуль генерации")
            return
        }
        val total = _state.value.count
        val base = total / modules.size
        val remainder = total % modules.size
        val output = mutableListOf<String>()

        modules.forEachIndexed { moduleIndex, module ->
            val amount = base + if (moduleIndex < remainder) 1 else 0
            repeat(amount) {
                val character = weighted(s.characters) ?: s.properNames.randomOrNull()?.let { WeightedEntity(it) }
                val place = weighted(s.places)
                val sentence = when (module) {
                    GenerationModule.DIALOGUE -> {
                        val phrase = weightedPhrase(s.phrases)?.text
                        val speaker = character?.name ?: "Он"
                        if (phrase != null) "— ${phrase.trim().trimEnd('.', '!', '?')} — сказал ${speaker}."
                        else "— Я должен идти, — сказал ${speaker}."
                    }
                    GenerationModule.DESCRIPTION -> {
                        val raw = chain.sentence()
                        if (place != null) "${place.name}: ${raw}" else raw
                    }
                    GenerationModule.ACTION -> chain.sentence()
                    GenerationModule.EVENT -> {
                        val raw = chain.sentence()
                        if (it == 0) "В этот момент ${raw.replaceFirstChar { c -> c.lowercase() }}" else raw
                    }
                }
                output += sentence
            }
        }
        _state.value = _state.value.copy(output = output.joinToString("\n\n"))
    }

    private fun weighted(items: List<WeightedEntity>): WeightedEntity? =
        items.flatMap { item -> List(item.importance.coerceIn(1, 10)) { item } }.randomOrNull()

    private fun weightedPhrase(items: List<WeightedPhrase>): WeightedPhrase? =
        items.flatMap { item -> List(item.weight.coerceIn(1, 10)) { item } }.randomOrNull()
}
