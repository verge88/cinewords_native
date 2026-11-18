package com.cinewords.nativeapp

/** Matches Flutter's shadow-repetition comparison: typed recall vs target phrase. */
object ExerciseMetrics {
    fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun distance(from: String, to: String): Int {
        val a = normalize(from)
        val b = normalize(to)
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            current.copyInto(previous)
        }
        return previous[b.length]
    }

    fun similarity(typed: String, target: String): Double {
        val length = normalize(target).length.coerceAtLeast(1)
        return (1.0 - distance(typed, target).toDouble() / length).coerceIn(0.0, 1.0)
    }
}
