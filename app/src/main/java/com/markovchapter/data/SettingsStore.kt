package com.markovchapter.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("markov", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        fun readEntities(key: String): List<WeightedEntity> = runCatching {
            val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val item = array.getJSONObject(i)
                    val name = item.optString("name").trim()
                    if (name.isBlank()) null else WeightedEntity(name, item.optInt("importance", 5).coerceIn(1, 10))
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

        val names = runCatching {
            val a = JSONArray(prefs.getString("names", "[]") ?: "[]")
            (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())

        val phrases = runCatching {
            val a = JSONArray(prefs.getString("phrases", "[]") ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val o = a.getJSONObject(i)
                    o.optString("text").trim().takeIf(String::isNotBlank)?.let {
                        WeightedPhrase(it, o.optInt("weight", 5).coerceIn(1, 10))
                    }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

        val modules = runCatching {
            val a = JSONArray(prefs.getString("modules", "[]") ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val item = a.get(i)
                    if (item is JSONObject) {
                        val module = GenerationModule.valueOf(item.getString("module"))
                        ModuleStep(item.optString("id").ifBlank { UUID.randomUUID().toString() }, module)
                    } else ModuleStep(module = GenerationModule.valueOf(item.toString()))
                }.getOrNull()
            }
        }.getOrDefault(emptyList()).ifEmpty { GenerationModule.values().map { ModuleStep(module = it) } }

        return AppSettings(
            characters = readEntities("characters"),
            places = readEntities("places"),
            properNames = names,
            phrases = phrases,
            modules = modules
        )
    }

    fun save(settings: AppSettings): Boolean {
        fun entities(items: List<WeightedEntity>) = JSONArray().apply {
            items.forEach { put(JSONObject().put("name", it.name).put("importance", it.importance)) }
        }.toString()

        return prefs.edit()
            .putInt("settings_version", 2)
            .putString("characters", entities(settings.characters))
            .putString("places", entities(settings.places))
            .putString("names", JSONArray(settings.properNames).toString())
            .putString("phrases", JSONArray().apply {
                settings.phrases.forEach { put(JSONObject().put("text", it.text).put("weight", it.weight)) }
            }.toString())
            .putString("modules", JSONArray().apply {
                settings.modules.forEach { put(JSONObject().put("id", it.id).put("module", it.module.name)) }
            }.toString())
            .commit()
    }
}
