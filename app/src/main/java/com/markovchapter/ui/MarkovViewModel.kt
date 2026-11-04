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

data class UiState(val trainedWords:Int=0,val training:Boolean=false,val status:String="Загрузите текстовый документ",val settings:AppSettings=AppSettings(),val output:String="",val count:Int=100,val stats:TrainingStats=TrainingStats())
class MarkovViewModel(app:Application):AndroidViewModel(app){
 private val chain=MarkovChain();private val store=SettingsStore(app);private val extractor=DocumentTextExtractor(app)
 private val _state=MutableStateFlow(UiState(settings=store.load()));val state=_state.asStateFlow()
 fun importDocument(uri:Uri)=viewModelScope.launch{
  _state.value=_state.value.copy(training=true,status="Чтение документа…")
  runCatching{val doc=withContext(Dispatchers.IO){extractor.read(uri)};_state.value=_state.value.copy(status="Обучение: ${doc.name}")
   val stats=withContext(Dispatchers.Default){chain.train(doc.text);chain.stats}
   _state.value=_state.value.copy(training=false,trainedWords=stats.words,stats=stats,status="Добавлен файл: ${doc.name}")
  }.onFailure{e->_state.value=_state.value.copy(training=false,status="Ошибка импорта: ${e.message?:"не удалось прочитать файл"}")}
 }
 fun clear(){chain.clear();_state.value=_state.value.copy(trainedWords=0,stats=chain.stats,status="Модель очищена",output="")}
 fun settings(s:AppSettings){store.save(s);_state.value=_state.value.copy(settings=s)}
 fun count(n:Int){_state.value=_state.value.copy(count=n.coerceIn(1,1000))}
 fun addModule(m:GenerationModule){settings(_state.value.settings.copy(modules=_state.value.settings.modules+ModuleStep(module=m)))}
 fun removeModule(id:String){settings(_state.value.settings.copy(modules=_state.value.settings.modules.filterNot{it.id==id}))}
 fun moveModule(id:String,delta:Int){val list=_state.value.settings.modules.toMutableList();val from=list.indexOfFirst{it.id==id};val to=from+delta;if(from<0||to !in list.indices)return;val item=list.removeAt(from);list.add(to,item);settings(_state.value.settings.copy(modules=list))}
 fun generate(){
  val current=_state.value;val cfg=current.settings
  if(!chain.isTrained()){_state.value=current.copy(status="Сначала обучите модель");return}
  if(cfg.modules.isEmpty()){_state.value=current.copy(status="Добавьте хотя бы один модуль");return}
  val output=mutableListOf<String>();val used=mutableSetOf<String>()
  val people=(cfg.characters.map{it.name}+cfg.properNames).distinct()
  val states=people.associateWith{Condition()}.toMutableMap()
  var place=cfg.places.maxByOrNull{it.importance}?.name
  var atmosphere="неизвестная обстановка";var lastDescription="";var lastSpeaker:String?=null;var eventNo=0
  repeat(current.count){index->
   val module=cfg.modules[index%cfg.modules.size].module
   val phase=when{index< (current.count*.2f).toInt().coerceAtLeast(1)->0;index>=(current.count*.8f).toInt()->2;else->1}
   val line=when(module){
    GenerationModule.DESCRIPTION->{val raw=fresh(used);place=weighted(cfg.places)?.name?:place;atmosphere=inferAtmosphere(raw);lastDescription=listOfNotNull(place,atmosphere,raw).joinToString(", ");remember(used,if(place!=null)"В ${place}: ${raw}" else raw)}
    GenerationModule.ACTION->{val actor=choose(people,lastSpeaker);val raw=fresh(used);val lead=when{phase==2->"Теперь, когда всё решилось, ";actor!=null&&(states[actor]?.fear?:0)>=2->"${actor}, преодолевая страх, ";actor!=null&&(states[actor]?.resolve?:0)>=2->"${actor}, собравшись с силами, ";lastDescription.isNotBlank()->"Под влиянием этой обстановки, ";else->"Не теряя времени, "};val tail=place?.let{" у ${it}"}?:"";remember(used,if(actor!=null)"${lead}${actor} ${raw.replaceFirstChar{it.lowercase()}.trimEnd('.', '!', '?')}${tail}." else lead+raw)}
    GenerationModule.EVENT->{val event=listOf("опасность","открытие","потеря","неожиданная помощь","предательство")[eventNo++%5];val affected=people.shuffled().take(if(people.size>1)2 else 1);affected.forEach{name->val old=states[name]?:Condition();states[name]=when(event){"опасность"->old.copy(fear=(old.fear+1).coerceAtMost(3),resolve=(old.resolve+1).coerceAtMost(3));"открытие"->old.copy(knowledge=(old.knowledge+1).coerceAtMost(3),fear=(old.fear-1).coerceAtLeast(0));"потеря"->old.copy(fear=(old.fear+1).coerceAtMost(3),resolve=(old.resolve-1).coerceAtLeast(0));"неожиданная помощь"->old.copy(resolve=(old.resolve+1).coerceAtMost(3),fear=(old.fear-1).coerceAtLeast(0));else->old.copy(fear=(old.fear+1).coerceAtMost(3),knowledge=(old.knowledge+1).coerceAtMost(3))}}
     val raw=fresh(used);remember(used,if(affected.isNotEmpty())"Событие изменило положение: ${affected.joinToString(" и ")} столкнулись с тем, что означало ${event}. ${raw}" else "Произошло событие: ${event}. ${raw}")}
    GenerationModule.DIALOGUE->{val speaker=choose(people,lastSpeaker);val listener=people.firstOrNull{it!=speaker};val c=speaker?.let{states[it]}?:Condition();val phrase=weightedPhrase(cfg.phrases)?:WeightedPhrase(when{phase==0->"Нам нужно понять, что здесь происходит";phase==2->"Теперь я понимаю, что нам делать дальше";c.fear>=2->"Я не уверен, что мы выберемся отсюда";c.knowledge>=2->"Теперь мне стало ясно, что произошло";else->"Нам придётся действовать вместе"});lastSpeaker=speaker;remember(used,if(speaker!=null)"— ${phrase.text.trim().trimEnd('.', '!', '?')} — сказал ${speaker}${listener?.let{" ${it}"}?:""}." else "— ${phrase.text.trim().trimEnd('.', '!', '?')}.")}
   }
   output+=when{index==0->"Всё началось с того, что ${line.replaceFirstChar{it.lowercase()}}";phase==2&&index==(current.count*.8f).toInt()->"Постепенно всё пришло к завершению. ${line}";else->line}
  }
  _state.value=current.copy(output=output.joinToString("\n\n"),status="Создано предложений: ${output.size}")
 }
 private fun fresh(used:Set<String>)=chain.sentence(used)
 private fun remember(used:MutableSet<String>,s:String):String{used.add(s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+")," ").trim());return s}
 private fun choose(names:List<String>,previous:String?)=names.filter{it!=previous}.randomOrNull()?:names.randomOrNull()
 private fun weighted(items:List<WeightedEntity>)=items.flatMap{x->List(x.importance.coerceIn(1,10)){x}}.randomOrNull()
 private fun weightedPhrase(items:List<WeightedPhrase>)=items.flatMap{x->List(x.weight.coerceIn(1,10)){x}}.randomOrNull()
 private fun inferAtmosphere(s:String):String{val t=s.lowercase();return when{listOf("темн","мрак","ноч","тень","черн").any{it in t}->"в тревожном полумраке";listOf("холод","лёд","лед","мороз","снег").any{it in t}->"в холодной обстановке";listOf("дожд","влаж","мокр","гряз","плес").any{it in t}->"в сырой и тяжёлой атмосфере";listOf("свет","солн","ярк","тепл").any{it in t}->"в ярком свете";else->"в напряжённой тишине"}}
 private data class Condition(val fear:Int=0,val resolve:Int=0,val knowledge:Int=0)
}
