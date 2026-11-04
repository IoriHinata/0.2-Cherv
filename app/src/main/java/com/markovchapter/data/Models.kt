package com.markovchapter.data
import java.util.UUID
data class WeightedEntity(val name:String,val importance:Int=5)
data class WeightedPhrase(val text:String,val weight:Int=5)
enum class GenerationModule(val title:String){DIALOGUE("Диалог"),DESCRIPTION("Описание"),ACTION("Действие"),EVENT("Событие")}
data class ModuleStep(val id:String=UUID.randomUUID().toString(),val module:GenerationModule)
data class AppSettings(val characters:List<WeightedEntity> = emptyList(),val places:List<WeightedEntity> = emptyList(),val properNames:List<String> = emptyList(),val phrases:List<WeightedPhrase> = emptyList(),val modules:List<ModuleStep> = GenerationModule.values().map{ModuleStep(module=it)})
data class TrainingStats(val words:Int=0,val uniqueWords:Int=0,val transitions:Int=0,val sourceSentences:Int=0,val vocabularyRatio:Float=0f,val recommendedWords:Int=50_000,val nouns:Int=0,val adjectives:Int=0,val verbs:Int=0,val adverbs:Int=0){
 val progress:Float get()=(words.toFloat()/recommendedWords).coerceIn(0f,1f)
 val wordsRemaining:Int get()=(recommendedWords-words).coerceAtLeast(0)
 val level:String get()=when{words<5_000->"Недостаточно материала";words<20_000->"Начальный";words<50_000->"Базовый";words<200_000->"Рабочий";words<500_000->"Хороший объём";else->"Большой корпус"}
 val advice:String get()=when{words<5_000->"Добавьте ещё хотя бы ${(5_000-words).coerceAtLeast(0)} слов. Пока модель будет часто повторяться.";words<50_000->"Для первых экспериментов материала достаточно, но желательно довести корпус до 50 000 слов.";words<200_000->"Базовый объём достигнут. Добавляйте тексты того же жанра и с похожей манерой речи.";else->"Объём хороший. Дальнейшее улучшение зависит от качества и однородности корпуса, а не только от числа слов."}
}
