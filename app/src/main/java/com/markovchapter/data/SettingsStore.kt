package com.markovchapter.data
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class SettingsStore(c:Context){
 private val p=c.getSharedPreferences("markov",Context.MODE_PRIVATE)
 fun load():AppSettings=runCatching{
  fun entities(key:String)=JSONArray(p.getString(key,"[]")).let{a->(0 until a.length()).map{i->a.getJSONObject(i).let{o->WeightedEntity(o.getString("name"),o.optInt("importance",5).coerceIn(1,10))}}}
  val names=JSONArray(p.getString("names","[]"));val phrases=JSONArray(p.getString("phrases","[]"));val a=JSONArray(p.getString("modules","[]"))
  val modules=(0 until a.length()).mapNotNull{i->runCatching{val item=a.get(i);if(item is JSONObject){ModuleStep(item.optString("id").ifBlank{java.util.UUID.randomUUID().toString()},GenerationModule.valueOf(item.getString("module")))}else ModuleStep(module=GenerationModule.valueOf(item.toString()))}.getOrNull()}.ifEmpty{GenerationModule.values().map{ModuleStep(module=it)}}
  AppSettings(entities("characters"),entities("places"),(0 until names.length()).map{names.getString(it)},(0 until phrases.length()).map{phrases.getJSONObject(it).let{o->WeightedPhrase(o.getString("text"),o.optInt("weight",5).coerceIn(1,10))}},modules)
 }.getOrDefault(AppSettings())
 fun save(s:AppSettings){
  fun entities(items:List<WeightedEntity>)=JSONArray().apply{items.forEach{put(JSONObject().put("name",it.name).put("importance",it.importance))}}.toString()
  p.edit().putString("characters",entities(s.characters)).putString("places",entities(s.places)).putString("names",JSONArray(s.properNames).toString())
   .putString("phrases",JSONArray().apply{s.phrases.forEach{put(JSONObject().put("text",it.text).put("weight",it.weight))}}.toString())
   .putString("modules",JSONArray().apply{s.modules.forEach{put(JSONObject().put("id",it.id).put("module",it.module.name))}}.toString()).apply()
 }
}
