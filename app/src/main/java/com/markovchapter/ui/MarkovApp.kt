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

@Composable fun MarkovApp(vm:MarkovViewModel){
 val s by vm.state.collectAsStateWithLifecycle(); var tab by remember{mutableIntStateOf(0)}; val c= LocalContext.current
 val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){u:Uri?->u?.let{uri->runCatching{c.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}}.getOrNull()?.let{vm.train(it,uri.lastPathSegment ?: "text.txt")}}}
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){u->if(u!=null)runCatching{c.contentResolver.openOutputStream(u)?.bufferedWriter()?.use{it.write(s.output)}}}
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("MarkovChapter",style=MaterialTheme.typography.headlineMedium)
  TabRow(selectedTabIndex=tab){listOf("Обучение","Сущности","Генерация").forEachIndexed{i,t->Tab(tab==i,{tab=i},text={Text(t)})}}
  Spacer(Modifier.height(12.dp))
  when(tab){
   0->Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(s.status);Text("Обработано слов: ${s.trainedWords}");if(s.training)LinearProgressIndicator(Modifier.fillMaxWidth());Button({pick.launch("text/plain")},enabled=!s.training){Text("Загрузить .txt")};OutlinedButton({vm.clear()}){Text("Очистить модель")};Text("Всё обучение выполняется локально на устройстве.")}
   1->SettingsScreen(s.settings,vm::settings)
   else->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text("Длина: ${s.count} предложений");Slider(s.count.toFloat(),{vm.count(it.toInt())},valueRange=5f..20f,steps=14)
    Button({vm.generate()},modifier=Modifier.fillMaxWidth()){Text("Сгенерировать главу")}
    OutlinedTextField(s.output,{},readOnly=true,label={Text("Результат")},modifier=Modifier.fillMaxWidth().heightIn(min=220.dp))
    Row{OutlinedButton({(c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Глава",s.output))},enabled=s.output.isNotBlank(),modifier=Modifier.weight(1f)){Text("Копировать")};Spacer(Modifier.width(8.dp));OutlinedButton({save.launch("chapter.txt")},enabled=s.output.isNotBlank(),modifier=Modifier.weight(1f)){Text("Сохранить")}}
   }
  }
 }
}
@Composable private fun SettingsScreen(s:AppSettings,change:(AppSettings)->Unit){
 var character by remember{mutableStateOf("")};var place by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var phrase by remember{mutableStateOf("")};var weight by remember{mutableIntStateOf(5)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
  Text("Персонажи",style=MaterialTheme.typography.titleLarge)
  Row{OutlinedTextField(character,{character=it},label={Text("Имя")},modifier=Modifier.weight(1f));Button({if(character.isNotBlank()){change(s.copy(characters=s.characters+WeightedEntity(character.trim(),weight)));character=""}}){Text("+")}}
  Text("Важность: $weight");Slider(weight.toFloat(),{weight=it.toInt().coerceIn(1,10)},valueRange=1f..10f,steps=8)
  s.characters.forEachIndexed{i,e->Row{Text("${e.name} · ${e.importance}",Modifier.weight(1f));TextButton({change(s.copy(characters=s.characters.filterIndexed{n,_->n!=i}))}){Text("Удалить")}}}
  Divider();Text("Места",style=MaterialTheme.typography.titleLarge)
  Row{OutlinedTextField(place,{place=it},label={Text("Название места")},modifier=Modifier.weight(1f));Button({if(place.isNotBlank()){change(s.copy(places=s.places+WeightedEntity(place.trim(),weight)));place=""}}){Text("+")}}
  s.places.forEachIndexed{i,e->Row{Text("${e.name} · ${e.importance}",Modifier.weight(1f));TextButton({change(s.copy(places=s.places.filterIndexed{n,_->n!=i}))}){Text("Удалить")}}}
  Divider();Text("Имена собственные",style=MaterialTheme.typography.titleLarge)
  Row{OutlinedTextField(name,{name=it},label={Text("Имя")},modifier=Modifier.weight(1f));Button({if(name.isNotBlank()){change(s.copy(properNames=s.properNames+name.trim()));name=""}}){Text("+")}}
  s.properNames.forEachIndexed{i,v->Row{Text(v,Modifier.weight(1f));TextButton({change(s.copy(properNames=s.properNames.filterIndexed{n,_->n!=i}))}){Text("Удалить")}}}
  Divider();Text("Свои фразы",style=MaterialTheme.typography.titleLarge)
  OutlinedTextField(phrase,{phrase=it},label={Text("Реплика или предложение")},modifier=Modifier.fillMaxWidth())
  Button({if(phrase.isNotBlank()){change(s.copy(phrases=s.phrases+WeightedPhrase(phrase.trim(),weight)));phrase=""}}){Text("Добавить фразу · вес $weight")}
  s.phrases.forEachIndexed{i,v->Row{Text("${v.text} · вес ${v.weight}",Modifier.weight(1f));TextButton({change(s.copy(phrases=s.phrases.filterIndexed{n,_->n!=i}))}){Text("Удалить")}}}
 }
}
