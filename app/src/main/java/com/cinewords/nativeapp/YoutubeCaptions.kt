package com.cinewords.nativeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Attempts to read YouTube's publicly exposed timed-text tracks from a watch page.
 * This is a fallback for videos not yet cached in Supabase; availability varies by video/region.
 * Uses json3 timedtext, NOT YouTube Data API caption download (which requires owner OAuth).
 */
object YoutubeCaptions {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun fetch(videoId: String, language: String): List<Subtitle> {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return emptyList()
        val html = get("https://www.youtube.com/watch?v=" + videoId + "&hl=en")
        val playerResponse = extractPlayerResponse(html) ?: return emptyList()
        val tracks = JSONObject(playerResponse).optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")?.objects().orEmpty()
        val direct = tracks.firstOrNull {
            it.optString("languageCode").equals(language, ignoreCase = true)
        } ?: tracks.firstOrNull {
            it.optString("languageCode").startsWith(language + "-", ignoreCase = true)
        }
        val baseTrack = direct ?: if (language != "en") {
            tracks.firstOrNull { it.optString("languageCode").startsWith("en") }
        } else null
        if (baseTrack == null) return emptyList()
        val originalUrl = baseTrack.optString("baseUrl")
        if (!originalUrl.startsWith("https://")) return emptyList()
        val url = originalUrl.toHttpUrl().newBuilder()
            .setQueryParameter("fmt", "json3")
        if (direct == null) url.setQueryParameter("tlang", language)
        val json = JSONObject(get(url.build().toString()))
        return parseJson3(json, language)
    }

    /** Pure transformation of the json3 cue format, shared by manual and ASR captions. */
    fun parseJson3(json: JSONObject, language: String): List<Subtitle> {
        val events = json.optJSONArray("events")?.objects().orEmpty()
        return events.mapNotNull { event ->
            val segments = event.optJSONArray("segs")?.objects().orEmpty()
            val line = segments.joinToString("") { it.optString("utf8") }
                .replace("\n", " ")
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\s+"), " ").trim()
            if (line.isBlank()) return@mapNotNull null
            val start = event.optLong("tStartMs")
            val duration = event.optLong("dDurationMs", 2000L).coerceAtLeast(100L)
            Subtitle(start, start + duration, line, language)
        }
    }

    /** Match a JSON object while respecting escaped strings and nested braces. */
    private fun extractPlayerResponse(page: String): String? {
        val match = Regex("""(?:var\s+)?ytInitialPlayerResponse\s*=\s*\{""").find(page)
            ?: return null
        val start = page.indexOf('{', match.range.first)
        if (start < 0) return null
        var depth = 0
        var inQuotes = false
        var escaped = false
        for (i in start until page.length) {
            val c = page[i]
            if (inQuotes) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inQuotes = false
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return page.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("YouTube captions HTTP " + response.code)
            response.body?.string().orEmpty()
        }
    }
}
