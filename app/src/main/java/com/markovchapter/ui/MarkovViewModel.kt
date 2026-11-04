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
    val trainedWords:Int=0, val training:Boolean=false, val status:String="Загрузите текстовый документ",
    val settings:AppSettings=AppSettings(), val output:String="", val count:Int=100, val stats:TrainingStats=TrainingStats()
)

class MarkovViewModel(app:Application):AndroidViewModel(app) {
    private val writer=PrimitiveWriter()
    private val store=SettingsStore(app)
    private val extractor=DocumentTextExtractor(app)
    private val _state=MutableStateFlow(UiState(settings=store.load()))
    val state=_state.asStateFlow()

    fun importDocument(uri:Uri)=viewModelScope.launch {
        _state.value=_state.value.copy(training=true,status="Чтение документа…")
        runCatching {
            val doc=withContext(Dispatchers.IO){extractor.read(uri)}
            _state.value=_state.value.copy(status="Обучение: ${doc.name}")
            val stats=withContext(Dispatchers.Default){writer.train(doc.text);writer.stats()}
            _state.value=_state.value.copy(training=false,trainedWords=stats.words,stats=stats,status="Добавлен файл: ${doc.name}")
        }.onFailure { e ->
            _state.value=_state.value.copy(training=false,status="Ошибка импорта: ${e.message?:"не удалось прочитать файл"}")
        }
    }

    fun clear() {
        writer.clear()
        _state.value=_state.value.copy(trainedWords=0,stats=writer.stats(),status="Модель очищена",output="")
    }

    fun settings(s:AppSettings) {
        val saved=store.save(s)
        _state.value=_state.value.copy(settings=s,status=if(saved)"Изменения сохранены" else "Не удалось сохранить изменения на устройстве")
    }

    fun count(n:Int) { _state.value=_state.value.copy(count=n.coerceIn(1,1000)) }
    fun addModule(m:GenerationModule) { settings(_state.value.settings.copy(modules=_state.value.settings.modules+ModuleStep(module=m))) }
    fun removeModule(id:String) { settings(_state.value.settings.copy(modules=_state.value.settings.modules.filterNot{it.id==id})) }
    fun moveModule(id:String,delta:Int) {
        val list=_state.value.settings.modules.toMutableList()
        val from=list.indexOfFirst{it.id==id}; val to=from+delta
        if(from<0||to !in list.indices)return
        val item=list.removeAt(from);list.add(to,item)
        settings(_state.value.settings.copy(modules=list))
    }

    fun generate() {
        val current=_state.value
        if(!writer.isTrained()) { _state.value=current.copy(status="Сначала обучите модель"); return }
        val cfg=current.settings
        val result=writer.generate(current.count,cfg.characters.map{it.name}+cfg.properNames,cfg.places.map{it.name},cfg.phrases.map{it.text})
        _state.value=current.copy(output=result,status="Создано предложений: ${current.count}. Текст собран из языковых примитивов.")
    }
}
