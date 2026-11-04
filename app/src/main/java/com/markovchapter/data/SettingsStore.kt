package com.markovchapter.data
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class SettingsStore(c:Context) {
 private val p=c.getSharedPreferences("markov",Context.MODE_PRIVATE)
 fun load():AppSettings=runCatching {
  fun ents(k:String)=JSONArray(p.getString(k,"[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> WeightedEntity(o.getString("name"),o.optInt("importance",5).coerceIn(1,10)) } } }
  val n=JSONArray(p.getString("names","[]")); val f=JSONArray(p.getString("phrases","[]"))
  AppSettings(ents("characters"),ents("places"),(0 until n.length()).map{n.getString(it)},(0 until f.length()).map{f.getJSONObject(it).let{o->WeightedPhrase(o.getString("text"),o.optInt("weight",5).coerceIn(1,10))}})
 }.getOrDefault(AppSettings())
 fun save(s:AppSettings) {
  fun ents(xs:List<WeightedEntity>)=JSONArray().apply{xs.forEach{put(JSONObject().put("name",it.name).put("importance",it.importance))}}.toString()
  p.edit().putString("characters",ents(s.characters)).putString("places",ents(s.places)).putString("names",JSONArray(s.properNames).toString())
   .putString("phrases",JSONArray().apply{s.phrases.forEach{put(JSONObject().put("text",it.text).put("weight",it.weight))}}.toString()).apply()
 }
}
