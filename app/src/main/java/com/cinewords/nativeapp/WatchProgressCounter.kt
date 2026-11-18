package com.cinewords.nativeapp

/**
 * Counts listening/watching time, not seek position.
 * Paused/buffering seconds are excluded; periodically drains for persistence.
 */
class WatchProgressCounter(private val reportIntervalSeconds: Int = 30) {
    init { require(reportIntervalSeconds > 0) }
    private var unreportedSeconds = 0

    fun tick(playing: Boolean): Boolean {
        if (playing) unreportedSeconds++
        return unreportedSeconds >= reportIntervalSeconds
    }

    fun drain(): Int {
        val result = unreportedSeconds
        unreportedSeconds = 0
        return result
    }
}
