package com.markovchapter.data
data class WeightedEntity(val name:String,val importance:Int=5)
data class WeightedPhrase(val text:String,val weight:Int=5)
data class AppSettings(val characters:List<WeightedEntity> = emptyList(),val places:List<WeightedEntity> = emptyList(),val properNames:List<String> = emptyList(),val phrases:List<WeightedPhrase> = emptyList())
