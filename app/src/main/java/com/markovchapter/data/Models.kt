package com.markovchapter.data

data class WeightedEntity(val name: String, val importance: Int = 5)
data class WeightedPhrase(val text: String, val weight: Int = 5)

enum class GenerationModule(val title: String) {
    DIALOGUE("Диалог"),
    DESCRIPTION("Описание"),
    ACTION("Действие"),
    EVENT("Событие")
}

data class AppSettings(
    val characters: List<WeightedEntity> = emptyList(),
    val places: List<WeightedEntity> = emptyList(),
    val properNames: List<String> = emptyList(),
    val phrases: List<WeightedPhrase> = emptyList(),
    val modules: List<GenerationModule> = GenerationModule.values().toList()
)
