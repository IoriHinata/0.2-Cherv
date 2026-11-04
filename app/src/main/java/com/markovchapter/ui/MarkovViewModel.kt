package com.markovchapter.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.markovchapter.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(val trainedWords:Int=0,val training:Boolean=false,val status:String="Загрузите .txt-файл",val settings:AppSettings=AppSettings(),val output:String="",val count:Int=10)
class MarkovViewModel(app:Application):AndroidViewModel(app) {
 private val chain=MarkovChain(); private val store=SettingsStore(app)
 private val _state=MutableStateFlow(UiState(settings=store.load())); val state=_state.asStateFlow()
 fun train(text:String,name:String)=viewModelScope.launch {
  _state.value=_state.value.copy(training=true,status="Обучение: $name")
  val count=withContext(Dispatchers.Default){chain.train(text);chain.wordCount}
  _state.value=_state.value.copy(training=false,trainedWords=count,status=if(chain.isTrained())"Обучение завершено" else "Недостаточно текста")
 }
 fun clear(){chain.clear();_state.value=_state.value.copy(trainedWords=0,status="Модель очищена",output="")}
 fun settings(s:AppSettings){store.save(s);_state.value=_state.value.copy(settings=s)}
 fun count(n:Int){_state.value=_state.value.copy(count=n.coerceIn(5,20))}
 fun generate(){
  if(!chain.isTrained()){_state.value=_state.value.copy(status="Сначала обучите модель");return}
  val s=_state.value.settings; val out=mutableListOf<String>()
  repeat(_state.value.count){i->
   val person=s.characters.flatMap{e->List(e.importance.coerceIn(1,10)){e}}.randomOrNull()
   val place=s.places.flatMap{e->List(e.importance.coerceIn(1,10)){e}}.randomOrNull()
   out.add((if(i==0 && place!=null) "В ${place.name}. " else "")+chain.sentence())
   val phrase=s.phrases.flatMap{e->List(e.weight.coerceIn(1,10)){e}}.randomOrNull()
   if(phrase!=null && (0..2).random()==0) out.add("— ${phrase.text} — сказал ${s.properNames.randomOrNull() ?: person?.name ?: "Он"}.")
   else if(person!=null && (0..3).random()==0) out.add("— Я должен идти, — сказал ${person.name}.")
  }
  _state.value=_state.value.copy(output=out.joinToString("\n\n"))
 }
}
