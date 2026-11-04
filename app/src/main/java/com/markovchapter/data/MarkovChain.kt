package com.markovchapter.data

class MarkovChain {
 private val next = mutableMapOf<Pair<String,String>, MutableList<String>>()
 private val starts = mutableListOf<Pair<String,String>>()
 var wordCount = 0; private set
 fun train(text:String) {
  val t = Regex("[\\p{L}\\p{N}]+(?:[-’'][\\p{L}\\p{N}]+)*|[.!?…,:;—-]").findAll(text.lowercase()).map { it.value }.toList()
  wordCount += t.count { it.firstOrNull()?.isLetterOrDigit() == true }
  for(i in 0 until (t.size-2).coerceAtLeast(0)) {
   val k=t[i] to t[i+1]; next.getOrPut(k){mutableListOf()}.add(t[i+2])
   if(i==0 || t[i-1] in endings) starts.add(k)
  }
 }
 fun clear(){next.clear();starts.clear();wordCount=0}
 fun isTrained()=next.isNotEmpty()
 fun sentence():String {
  if(next.isEmpty()) return ""
  var p=starts.randomOrNull() ?: next.keys.random()
  val out= mutableListOf(p.first,p.second)
  for(i in 0 until 26) {
   val n=next[p]?.randomOrNull() ?: break
   out.add(n); if(n in endings) break
   p=p.second to n
  }
  val s=out.joinToString(" ").replace(" ,",",").replace(" .",".").replace(" !","!").replace(" ?","?").replace(" …","…")
  return s.replaceFirstChar { it.uppercase() }.let { if(it.lastOrNull() in endings) it else "$it." }
 }
 companion object { private val endings=setOf(".","!","?","…") }
}
