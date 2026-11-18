package com.cinewords.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressCounterTest {
    @Test fun countsOnlyActualPlaybackSeconds() {
        val counter = WatchProgressCounter(3)
        assertFalse(counter.tick(false))
        assertFalse(counter.tick(true))
        assertFalse(counter.tick(false))
        assertFalse(counter.tick(true))
        assertTrue(counter.tick(true))
        assertEquals(3, counter.drain())
        assertEquals(0, counter.drain())
    }

    @Test fun flushesPartialSessionOnExit() {
        val counter = WatchProgressCounter(30)
        repeat(8) { assertFalse(counter.tick(true)) }
        assertEquals(8, counter.drain())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidInterval() {
        WatchProgressCounter(0)
    }
}
