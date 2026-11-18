package com.cinewords.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMetricsTest {
    @Test fun normalizationIgnoresPunctuationAndCase() {
        assertEquals("could you repeat that",
            ExerciseMetrics.normalize("Could YOU repeat that?!"))
    }

    @Test fun editDistanceMatchesExpectedValues() {
        assertEquals(0, ExerciseMetrics.distance("hello", "Hello!"))
        assertEquals(1, ExerciseMetrics.distance("kitten", "sitten"))
        assertEquals(3, ExerciseMetrics.distance("kitten", "sitting"))
    }

    @Test fun emptyAndPerfectGuesses() {
        assertEquals(0.0, ExerciseMetrics.similarity("", "hello"), 0.00001)
        assertEquals(1.0, ExerciseMetrics.similarity("THANK you!", "thank you"), 0.00001)
        assertTrue(ExerciseMetrics.similarity("tank you", "thank you") < 1.0)
    }
}
