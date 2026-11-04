package com.markovchapter.data
class MarkovChain{
 private val next=mutableMapOf<Pair<String,String>,MutableList<String>>()
 private val starts=mutableListOf<Pair<String,String>>()
 private val vocabulary=mutableSetOf<String>()
 private var sourceSentences=0
 var wordCount=0;private set
 val stats:TrainingStats get()=TrainingStats(wordCount,vocabulary.size,next.values.sumOf{it.size},sourceSentences,if(wordCount==0)0f else vocabulary.size.toFloat()/wordCount)
 @Synchronized fun train(text:String){
  val tokens=Regex("[\\p{L}\\p{N}]+(?:[-’'][\\p{L}\\p{N}]+)*|[.!?…,:;—-]").findAll(text.lowercase()).map{it.value}.toList()
  wordCount+=tokens.count{it.firstOrNull()?.isLetterOrDigit()==true}
  vocabulary.addAll(tokens.filter{it.firstOrNull()?.isLetterOrDigit()==true})
  sourceSentences+=tokens.count{it in endings}
  for(i in 0 until (tokens.size-2).coerceAtLeast(0)){val key=tokens[i] to tokens[i+1];next.getOrPut(key){mutableListOf()}.add(tokens[i+2]);if(i==0||tokens[i-1] in endings)starts.add(key)}
 }
 @Synchronized fun clear(){next.clear();starts.clear();vocabulary.clear();wordCount=0;sourceSentences=0}
 @Synchronized fun isTrained()=next.isNotEmpty()
 @Synchronized fun sentence(avoid:Set<String> = emptySet()):String{if(next.isEmpty())return "";repeat(12){val s=buildSentence();if(normalise(s) !in avoid)return s};return buildSentence()}
 private fun buildSentence():String{
  var pair=starts.randomOrNull()?:next.keys.random();val out=mutableListOf(pair.first,pair.second)
  for(i in 0 until 30){val n=next[pair]?.randomOrNull()?:break;out.add(n);if(n in endings)break;pair=pair.second to n}
  val s=out.joinToString(" ").replace(" ,",",").replace(" .",".").replace(" !","!").replace(" ?","?").replace(" …","…").replace(" :",":").replace(" ;",";")
  return s.replaceFirstChar{it.uppercase()}.let{if(it.takeLast(1) in endings)it else "$it."}
 }
 private fun normalise(s:String)=s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
 companion object{private val endings=setOf(".","!","?","…")}
}
