package com.cinewords.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSubtitlesApiTest {
    @Test fun normalizesImdbIdsLikeFlutter() {
        assertEquals("111161", OpenSubtitlesApi.normalizeImdbId("tt0111161"))
        assertEquals("1234", OpenSubtitlesApi.normalizeImdbId("0001234"))
        assertEquals("0", OpenSubtitlesApi.normalizeImdbId("0000"))
        assertNull(OpenSubtitlesApi.normalizeImdbId(""))
        assertNull(OpenSubtitlesApi.normalizeImdbId(null))
    }

    @Test fun extractsImdbFromMovieEmbed() {
        assertEquals("tt0111161",
            OpenSubtitlesApi.imdbIdFromUrl("https://example.com/movie/tt0111161"))
        assertNull(OpenSubtitlesApi.imdbIdFromUrl("https://example.com/movie/12345"))
    }
}
