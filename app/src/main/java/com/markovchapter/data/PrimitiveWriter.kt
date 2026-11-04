package com.markovchapter.data

import kotlin.random.Random

/** Small adaptive Russian primitive writer: corpus vocabulary + fixed grammar frames + paragraph blocks. */
class PrimitiveWriter {
    private val frequencies = linkedMapOf<String, Int>()
    private val nouns = linkedMapOf<String, Int>()
    private val adjectives = linkedMapOf<String, Int>()
    private val verbs = linkedMapOf<String, Int>()
    private val adverbs = linkedMapOf<String, Int>()
    var wordCount: Int = 0
        private set

    @Synchronized fun train(text: String) {
        val tokens = Regex("[А-Яа-яЁё]{3,}").findAll(text.lowercase()).map { it.value }.toList()
        wordCount += tokens.size
        tokens.forEach { word ->
            frequencies[word] = (frequencies[word] ?: 0) + 1
            when {
                isVerb(word) -> verbs[word] = (verbs[word] ?: 0) + 1
                isAdjective(word) -> adjectives[word] = (adjectives[word] ?: 0) + 1
                isAdverb(word) -> adverbs[word] = (adverbs[word] ?: 0) + 1
                isNoun(word) && word !in stopWords -> nouns[word] = (nouns[word] ?: 0) + 1
            }
        }
    }

    @Synchronized fun clear() {
        frequencies.clear(); nouns.clear(); adjectives.clear(); verbs.clear(); adverbs.clear(); wordCount = 0
    }

    @Synchronized fun stats(): TrainingStats {
        val vocab = frequencies.size
        return TrainingStats(wordCount, vocab, frequencies.values.sum(), 0,
            if (wordCount == 0) 0f else vocab.toFloat() / wordCount)
    }

    @Synchronized fun isTrained() = wordCount > 0

    @Synchronized fun generate(sentenceCount: Int, characters: List<String>, places: List<String>, phrases: List<String>): String {
        if (wordCount == 0) return ""
        val output = mutableListOf<String>()
        val used = mutableSetOf<String>()
        var previousBlock = -1
        var index = 0
        while (index < sentenceCount) {
            val block = (0..4).filter { it != previousBlock }.random()
            previousBlock = block
            val size = minOf(sentenceCount - index, Random.nextInt(2, 5))
            repeat(size) {
                if (index < sentenceCount) {
                    output += makeSentence(block, it, characters.randomOrNull(), places.randomOrNull(), phrases, used)
                    index++
                }
            }
        }
        return output.chunked(3).joinToString("\n\n") { it.joinToString(" ") }
    }

    private fun makeSentence(block: Int, offset: Int, person: String?, place: String?, phrases: List<String>, used: MutableSet<String>): String {
        val noun = pick(nouns, listOf("тишина", "дорога", "память", "голос", "дом"))
        val adjective = pick(adjectives, listOf("странный", "тревожный", "пустой", "далёкий"))
        val verb = pick(verbs, listOf("ждать", "смотреть", "помнить", "искать", "решать"))
        val adverb = pick(adverbs, listOf("медленно", "внезапно", "тихо", "осторожно"))
        val name = person?.takeIf { it.isNotBlank() } ?: "человек"
        val location = place?.takeIf { it.isNotBlank() } ?: "комната"
        val candidates = when (block) {
            0 -> listOf(
                "Всё началось с обычной тишины.",
                "Вокруг стояла тишина, и каждый звук казался важным.",
                "Место казалось чужим, хотя слово «$location» было знакомо.",
                "В памяти снова возникло слово «$noun»."
            )
            1 -> listOf(
                "$name остановился и посмотрел вокруг.",
                "$name заметил, что всё изменилось.",
                "$name попытался понять, что происходит.",
                "Он $adverb сделал шаг и замер."
            )
            2 -> listOf(
                "В голове возникла мысль: «$noun».",
                "Это казалось $adjective, но объяснения не было.",
                "Слово «$adjective» никак не выходило из памяти.",
                "Он вспомнил, как однажды приходилось $verb."
            )
            3 -> {
                val phrase = phrases.randomOrNull()?.trim()?.trimEnd('.', '!', '?')
                    ?.takeIf { it.isNotBlank() } ?: "Нам нужно решить, что делать дальше"
                listOf("— $phrase, — произнёс $name.", "— $phrase, — тихо сказал он.")
            }
            else -> listOf(
                "Всё ещё оставалось слишком много вопросов.",
                "Ответа не было, но отступать стало поздно.",
                "Тишина вернулась, словно ничего не произошло.",
                "И всё же эта история только начиналась."
            )
        }
        val fresh = candidates.filter { normalize(it) !in used }.randomOrNull() ?: candidates.random()
        used.add(normalize(fresh))
        return fresh
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^а-яё]+"), " ").trim()

    private fun <T> pick(map: Map<String, Int>, fallback: List<T>): String {
        val candidates = map.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(300)
        if (candidates.isEmpty()) return fallback.random().toString()
        var n = Random.nextInt(candidates.sumOf { it.value.coerceAtMost(100) })
        for (entry in candidates) {
            n -= entry.value.coerceAtMost(100)
            if (n < 0) return entry.key
        }
        return candidates.first().key
    }

    private fun isVerb(w: String) = w.endsWith("ть") || w.endsWith("ться") || w.endsWith("л") ||
        w.endsWith("ла") || w.endsWith("ли") || w.endsWith("ет") || w.endsWith("ит") || w.endsWith("ют") || w.endsWith("ут")
    private fun isAdjective(w: String) = w.endsWith("ый") || w.endsWith("ий") || w.endsWith("ая") ||
        w.endsWith("ое") || w.endsWith("ые") || w.endsWith("ого") || w.endsWith("ему") || w.endsWith("ыми")
    private fun isAdverb(w: String) = w.endsWith("о") || w.endsWith("ски")
    private fun isNoun(w: String) = w.endsWith("а") || w.endsWith("я") || w.endsWith("о") ||
        w.endsWith("е") || w.endsWith("ь") || w.endsWith("мя") || w.endsWith("ы") || w.endsWith("и")

    private val stopWords = setOf("это","что","как","так","его","ему","она","они","оно","для","при","над","под",
        "без","или","если","когда","тогда","чтобы","который","которая","которое","которые","быть","было",
        "были","есть","нет","уже","ещё","очень","только","даже","потом","здесь","там","куда","откуда",
        "перед","после","между","через","себя","него","нее","ним")
}
