package com.cinewords.nativeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles.com REST API v1, matching Flutter's OpenSubtitlesService.
 *
 * The key is a client-side credential imported at build time; never log it.
 * Downloads consume the provider's quota. Call only as a fallback when the
 * existing subtitle source has no usable captions.
 */
class OpenSubtitlesApi(
    private val apiKey: String = BuildConfig.OPENSUBTITLES_API_KEY,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS).build(),
) {
    companion object {
        private const val API = "https://api.opensubtitles.com/api/v1"

        /** OpenSubtitles expects IMDb numeric IDs with no tt prefix / leading zeroes. */
        fun normalizeImdbId(raw: String?): String? {
            val digits = raw?.trim()?.lowercase()?.removePrefix("tt")
                ?.filter(Char::isDigit)?.takeIf(String::isNotBlank) ?: return null
            return digits.trimStart('0').ifBlank { "0" }
        }

        /** Extract an IMDb ID from a movie embed path, not from unrelated numeric identifiers. */
        fun imdbIdFromUrl(url: String): String? =
            Regex("""(?:^|[^A-Za-z0-9])(tt[0-9]{7,9})(?:$|[^0-9])""")
                .find(url)?.groupValues?.getOrNull(1)
    }

    suspend fun fetchMovieSubtitles(tmdbId: Int?, imdbId: String?, language: String): List<Subtitle> {
        if (apiKey.isBlank() || language !in setOf("en", "ru")) return emptyList()
        val normalizedImdb = normalizeImdbId(imdbId)
        val hasTmdb = (tmdbId ?: 0) > 0
        if (!hasTmdb && normalizedImdb == null) return emptyList()

        val search = "$API/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("languages", language)
            .addQueryParameter("type", "movie")
            .addQueryParameter("order_by", "download_count")
            .addQueryParameter("order_direction", "desc")
            .apply {
                if (hasTmdb) addQueryParameter("tmdb_id", tmdbId.toString())
                else addQueryParameter("imdb_id", normalizedImdb!!)
            }.build().toString()

        val results = JSONObject(request(search)).optJSONArray("data") ?: JSONArray()
        val files = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until results.length()) {
            val attributes = results.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val count = attributes.optInt("download_count", 0)
            val entries = attributes.optJSONArray("files") ?: continue
            for (j in 0 until entries.length()) {
                val id = entries.optJSONObject(j)?.optInt("file_id", 0) ?: 0
                if (id > 0) files.add(id to count)
            }
        }
        // Avoid exhausting a free API key on many attempted subtitle downloads.
        for ((fileId, _) in files.distinctBy { it.first }.sortedByDescending { it.second }.take(2)) {
            val payload = JSONObject().put("file_id", fileId)
            val link = runCatching {
                JSONObject(request("$API/download", payload)).optString("link")
            }.getOrNull().orEmpty()
            if (!link.startsWith("https://", ignoreCase = true)) continue
            val parsed = runCatching {
                SubtitleParser.parse(request(link, isFile = true), language)
            }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private suspend fun request(url: String, body: JSONObject? = null, isFile: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
                .header("Accept", if (isFile) "*/*" else "application/json")
                .header("User-Agent", "CineWords/1.0")
            if (!isFile) builder.header("Api-Key", apiKey)
            if (body != null) builder.post(body.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType()))
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful)
                    throw ApiError(response.code, "OpenSubtitles HTTP " + response.code)
                text
            }
        }
}
