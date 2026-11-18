package com.cinewords.nativeapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Video(
    val id: String = "", val youtubeId: String = "", val title: String = "",
    val thumbnail: String = "", val channel: String = "", val description: String = "",
    val durationSec: Int = 0, val difficulty: String = "intermediate",
    val category: String = "general", val sourceType: String = "youtube",
    val videoUrl: String = "", val subtitleUrl: String = "", val subtitleUrlRu: String = "",
    val httpHeaders: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(j: JSONObject) = Video(
            id = j.optString("id"), youtubeId = j.optString("youtube_id"),
            title = j.optString("title"), thumbnail = j.optString("thumbnail_url"),
            channel = j.optString("channel_name", j.optString("author")),
            description = j.optString("description"),
            durationSec = j.optInt("duration_sec"), difficulty = j.optString("difficulty", "intermediate"),
            category = j.optString("category", "general"),
            sourceType = j.optString("source_type", "youtube"),
            videoUrl = j.optString("video_url"),
            subtitleUrl = j.optString("subtitle_url"), subtitleUrlRu = j.optString("subtitle_url_ru")
        )
    }
}

data class Word(
    val id: String, val word: String, val translation: String, val phonetic: String,
    val context: String, val type: String, val status: String,
    val nextReviewAt: String, val repetitionCount: Int
) {
    companion object {
        fun from(j: JSONObject) = Word(j.optString("id"), j.optString("word"),
            j.optString("translation"), j.optString("phonetic"),
            j.optString("context_sentence"), j.optString("item_type", "word"),
            j.optString("status", "new"), j.optString("next_review_at"),
            j.optInt("repetition_count"))
    }
}

data class Subtitle(val startMs: Long, val endMs: Long, val text: String, val language: String = "en") {
    companion object {
        fun from(j: JSONObject) = Subtitle(j.optLong("start_ms"), j.optLong("end_ms"),
            j.optString("text"), j.optString("language", "en"))
    }
}

data class Movie(val id: Int, val title: String, val image: String, val description: String,
    val year: String, val rating: Double, val imdbId: String, val tmdbId: Int) {
    companion object {
        fun from(j: JSONObject): Movie {
            val ext = j.optJSONObject("externalId")
            return Movie(j.optInt("id"), j.optString("name", j.optString("alternativeName")),
                j.optJSONObject("poster")?.optString("url").orEmpty(),
                j.optString("description"), j.optString("year"),
                j.optJSONObject("rating")?.optDouble("kp") ?: 0.0,
                ext?.optString("imdb").orEmpty(), ext?.optInt("tmdb") ?: 0)
        }
    }
    fun toVideo() = Video(id = "movie_$id", title = title, thumbnail = image,
        description = description, sourceType = "vidapi", youtubeId = tmdbId.toString(),
        videoUrl = "https://vidspark.to/movie/" + (imdbId.ifBlank { tmdbId.toString() }))
}

data class Profile(val name: String = "", val email: String = "", val streak: Int = 0,
    val minutes: Int = 0, val learned: Int = 0, val goal: Int = 15, val today: Int = 0)

data class Lookup(val word: String, val phonetic: String, val translation: String, val definition: String)

class ApiError(val status: Int, message: String) : Exception(message)

fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.optText(key: String): String = if (isNull(key)) "" else optString(key, "")

fun extractYouTubeId(input: String): String {
    val trimmed = input.trim()
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(trimmed)) return trimmed
    return try {
        val uri = java.net.URI(trimmed)
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        val videoQuery = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "v" }?.substringAfter('=', "")
        when {
            !videoQuery.isNullOrBlank() -> java.net.URLDecoder.decode(videoQuery, "UTF-8")
            uri.host?.contains("youtu.be") == true -> segments.firstOrNull().orEmpty()
            else -> {
                val ix = segments.indexOfFirst { it in listOf("embed", "shorts", "live") }
                if (ix >= 0) segments.getOrNull(ix + 1) ?: trimmed else trimmed
            }
        }
    } catch (_: Exception) { trimmed }
}

object SubtitleParser {
    fun parse(content: String, language: String = "en"): List<Subtitle> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val sections = normalized.split(Regex("\n\\s*\n"))
        val pattern = Regex("""(\d{2}:)?\d{2}:\d{2}[.,]\d{3}\s*-->\s*(\d{2}:)?\d{2}:\d{2}[.,]\d{3}""")
        return sections.mapNotNull { block ->
            val lines = block.lines()
            val lineIndex = lines.indexOfFirst { pattern.containsMatchIn(it) }
            if (lineIndex == -1) return@mapNotNull null
            val times = lines[lineIndex].split("-->")
            val start = timeMs(times[0].trim().split(" ").first())
            val end = timeMs(times[1].trim().split(" ").first())
            val text = lines.drop(lineIndex + 1).joinToString(" ")
                .replace(Regex("<[^>]+>"), "").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").trim()
            if (text.isBlank() || end <= start) null else Subtitle(start, end, text, language)
        }
    }
    private fun timeMs(input: String): Long {
        val parts = input.replace(',', '.').split(':')
        val seconds = parts.lastOrNull()?.toDoubleOrNull() ?: return 0
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0
        val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0
        return ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
    }
}

class CineApi(context: Context) {
    private val prefs = context.getSharedPreferences("cinewords_auth", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    var token by mutableStateOf(prefs.getString("access_token", null))
        private set
    var userId by mutableStateOf(prefs.getString("user_id", null))
        private set
    var email by mutableStateOf(prefs.getString("email", "").orEmpty())
        private set
    private var refreshToken: String? = prefs.getString("refresh_token", null)
    val isSignedIn get() = !token.isNullOrBlank() && !userId.isNullOrBlank()

    private fun storeSession(j: JSONObject, loginEmail: String = email) {
        val access = j.optString("access_token")
        if (access.isBlank()) return
        token = access
        refreshToken = j.optString("refresh_token", refreshToken.orEmpty())
        userId = j.optJSONObject("user")?.optString("id", userId.orEmpty()) ?: userId
        email = j.optJSONObject("user")?.optString("email", loginEmail) ?: loginEmail
        prefs.edit().putString("access_token", token).putString("refresh_token", refreshToken)
            .putString("user_id", userId).putString("email", email).apply()
    }

    private suspend fun httpText(url: String, method: String = "GET", body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(), anonymous: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " +
                    (if (anonymous) BuildConfig.SUPABASE_ANON_KEY else (token ?: BuildConfig.SUPABASE_ANON_KEY)))
                .header("Accept", "application/json")
            headers.forEach { (k, v) -> request.header(k, v) }
            if (method != "GET") request.method(method, (body?.toString() ?: "").toRequestBody(jsonType))
            http.newCall(request.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val explanation = runCatching { JSONObject(text).optString("msg",
                        JSONObject(text).optString("message", text)) }.getOrDefault(text)
                    throw ApiError(response.code, explanation.ifBlank { "HTTP " + response.code })
                }
                text
            }
        }

    private suspend fun api(path: String, method: String = "GET", body: JSONObject? = null,
        headers: Map<String, String> = emptyMap()): String {
        val url = BuildConfig.SUPABASE_URL + path
        return try { httpText(url, method, body, headers) }
        catch (e: ApiError) {
            if (e.status != 401 || refreshToken.isNullOrBlank()) throw e
            val refreshed = httpText(BuildConfig.SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token",
                "POST", JSONObject().put("refresh_token", refreshToken), anonymous = true)
            storeSession(JSONObject(refreshed))
            httpText(url, method, body, headers)
        }
    }

    suspend fun signIn(mail: String, password: String) {
        val result = httpText(BuildConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password",
            "POST", JSONObject().put("email", mail).put("password", password), anonymous = true)
        storeSession(JSONObject(result), mail)
        if (!isSignedIn) throw IllegalStateException("Подтвердите email и войдите снова")
    }

    suspend fun signUp(mail: String, password: String, name: String): Boolean {
        val result = httpText(BuildConfig.SUPABASE_URL + "/auth/v1/signup", "POST",
            JSONObject().put("email", mail).put("password", password)
                .put("data", JSONObject().put("display_name", name)), anonymous = true)
        storeSession(JSONObject(result), mail)
        return isSignedIn
    }

    suspend fun signOut() {
        runCatching { api("/auth/v1/logout", "POST", JSONObject()) }
        token = null; refreshToken = null; userId = null; email = ""
        prefs.edit().clear().apply()
    }

    private suspend fun table(name: String, query: Map<String, String>,
        method: String = "GET", body: JSONObject? = null, headers: Map<String, String> = emptyMap()): String {
        val url = (BuildConfig.SUPABASE_URL + "/rest/v1/" + name).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val path = url.build().toString().removePrefix(BuildConfig.SUPABASE_URL)
        return api(path, method, body, headers)
    }

    suspend fun featured(): List<Video> = JSONArray(table("videos",
        mapOf("select" to "*", "is_featured" to "eq.true", "order" to "created_at.desc",
            "limit" to "20"))).objects().map(Video::from)

    suspend fun searchLibrary(query: String): List<Video> =
        JSONArray(table("videos", mapOf("select" to "*",
            "title" to "ilike.*" + query.replace("*", "") + "*",
            "limit" to "40"))).objects().map(Video::from)

    suspend fun category(name: String): List<Video> =
        JSONArray(table("videos", mapOf("select" to "*", "category" to "eq.$name",
            "order" to "view_count.desc", "limit" to "40"))).objects().map(Video::from)

    suspend fun saveVideo(video: Video): Video {
        val j = JSONObject().put("youtube_id", video.youtubeId).put("title", video.title)
            .put("thumbnail_url", video.thumbnail).put("channel_name", video.channel)
            .put("description", video.description)
            .put("duration_sec", video.durationSec)
            .put("difficulty", when (video.difficulty) {
                "elementary" -> "beginner"; "upper_intermediate" -> "advanced"; else -> video.difficulty
            }).put("category", video.category)
        // Existing production videos table may lack the optional source_type and video_url columns.
        if (video.sourceType != "youtube") {
            j.put("source_type", video.sourceType).put("video_url", video.videoUrl)
        }
        val result = table("videos", mapOf("on_conflict" to "youtube_id", "select" to "*"),
            "POST", j, mapOf("Prefer" to "resolution=merge-duplicates,return=representation"))
        return Video.from(JSONArray(result).getJSONObject(0))
    }

    suspend fun favoriteIds(): Set<String> {
        val id = userId ?: return emptySet()
        return JSONArray(table("user_favorites", mapOf("select" to "video_id",
            "user_id" to "eq.$id"))).objects().map { it.optString("video_id") }.toSet()
    }

    suspend fun favorites(): List<Video> {
        val ids = favoriteIds()
        if (ids.isEmpty()) return emptyList()
        return JSONArray(table("videos", mapOf("select" to "*",
            "id" to "in.(" + ids.joinToString(",") + ")"))).objects().map(Video::from)
    }

    suspend fun favorite(video: Video, add: Boolean): Video {
        val v = if (video.id.isBlank() || !video.id.matches(
                Regex("[0-9a-fA-F-]{36}"))) saveVideo(video) else video
        val id = requireNotNull(userId)
        if (add) table("user_favorites", mapOf("on_conflict" to "user_id,video_id"),
            "POST", JSONObject().put("user_id", id).put("video_id", v.id),
            mapOf("Prefer" to "resolution=merge-duplicates"))
        else table("user_favorites", mapOf("user_id" to "eq.$id", "video_id" to "eq." + v.id),
            "DELETE", headers = mapOf("Prefer" to "return=minimal"))
        return v
    }

    suspend fun words(): List<Word> {
        val id = userId ?: return emptyList()
        return JSONArray(table("vocabulary", mapOf("select" to "*",
            "user_id" to "eq.$id", "order" to "created_at.desc"))).objects().map(Word::from)
    }

    suspend fun addWord(word: String, translation: String, phonetic: String = "",
        context: String = "", type: String = "word") {
        val id = requireNotNull(userId)
        table("vocabulary", mapOf("on_conflict" to "user_id,word"), "POST",
            JSONObject().put("user_id", id).put("word", word.trim().lowercase())
                .put("translation", translation).put("phonetic", phonetic)
                .put("context_sentence", context).put("item_type", type),
            mapOf("Prefer" to "resolution=merge-duplicates"))
    }

    suspend fun deleteWord(id: String) {
        table("vocabulary", mapOf("id" to "eq.$id"), "DELETE")
    }

    suspend fun review(id: String, quality: Int) {
        api("/rest/v1/rpc/update_vocabulary_sm2", "POST",
            JSONObject().put("p_vocab_id", id).put("p_quality", quality))
    }

    suspend fun subtitles(video: Video, lang: String): List<Subtitle> {
        if (Regex("^[0-9a-fA-F-]{36}$").matches(video.id)) {
            val cached = runCatching {
                JSONArray(table("subtitles", mapOf("select" to "*",
                    "video_id" to "eq." + video.id, "language" to "eq.$lang",
                    "order" to "sequence_index.asc"))).objects().map(Subtitle::from)
            }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) return cached
        }
        val url = if (lang == "ru") video.subtitleUrlRu else video.subtitleUrl
        val remote = url.ifBlank {
            when {
                video.sourceType == "youtube" && video.youtubeId.isNotBlank() ->
                    "https://www.youtube.com/api/timedtext?v=" + video.youtubeId + "&lang=" + lang + "&fmt=vtt"
                video.youtubeId.toIntOrNull() != null && video.sourceType in listOf("vidapi", "direct") ->
                    "https://cache.vdrk.site/v2/movie/" + video.youtubeId +
                        (if (lang == "ru") "/Russian.vtt" else "/English.vtt")
                else -> return emptyList()
            }
        }
        return runCatching {
            SubtitleParser.parse(getText(remote), lang)
        }.getOrDefault(emptyList())
    }

    suspend fun profile(): Profile {
        val id = userId ?: return Profile(email = email)
        val p = runCatching { JSONArray(table("profiles", mapOf("select" to "*",
            "id" to "eq.$id", "limit" to "1"))).optJSONObject(0) }.getOrNull()
        val today = java.time.LocalDate.now().toString()
        val day = runCatching { JSONArray(table("daily_stats", mapOf("select" to "*",
            "user_id" to "eq.$id", "date" to "eq.$today",
            "limit" to "1"))).optJSONObject(0) }.getOrNull()
        return Profile(p?.optString("display_name").orEmpty().ifBlank { email.substringBefore("@") },
            email, p?.optInt("streak_days") ?: 0,
            p?.optInt("total_watch_minutes") ?: 0,
            p?.optInt("total_words_learned") ?: 0,
            p?.optInt("daily_goal_minutes", 15) ?: 15,
            day?.optInt("minutes_watched") ?: 0)
    }

    suspend fun logWatch(videoId: String, positionMs: Long, watchedSec: Int) {
        if (!Regex("^[0-9a-fA-F-]{36}$").matches(videoId) || watchedSec <= 0) return
        runCatching {
            api("/rest/v1/rpc/log_watch_progress", "POST",
                JSONObject().put("p_user_id", userId).put("p_video_id", videoId)
                    .put("p_position_ms", positionMs).put("p_duration_sec", watchedSec))
        }
    }

    suspend fun youtube(query: String, nextPage: String = ""): Pair<List<Video>, String> {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) return searchLibrary(query) to ""
        val id = extractYouTubeId(query)
        if (id.matches(Regex("^[A-Za-z0-9_-]{11}$")) &&
            (query.contains("youtu") || query.trim() == id)) {
            return youtubeDetails(listOf(id)) to ""
        }
        val searchUrl = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet").addQueryParameter("q", query)
            .addQueryParameter("type", "video").addQueryParameter("videoCaption", "closedCaption")
            .addQueryParameter("videoEmbeddable", "true").addQueryParameter("relevanceLanguage", "en")
            .addQueryParameter("maxResults", "20").addQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
        if (nextPage.isNotBlank()) searchUrl.addQueryParameter("pageToken", nextPage)
        val result = JSONObject(getText(searchUrl.build().toString()))
        val ids = result.optJSONArray("items")?.objects()?.mapNotNull {
            it.optJSONObject("id")?.optString("videoId")
        }.orEmpty()
        return youtubeDetails(ids) to result.optString("nextPageToken")
    }

    private suspend fun youtubeDetails(ids: List<String>): List<Video> {
        if (ids.isEmpty()) return emptyList()
        val url = "https://www.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet,contentDetails")
            .addQueryParameter("id", ids.joinToString(","))
            .addQueryParameter("key", BuildConfig.YOUTUBE_API_KEY).build().toString()
        val entries = JSONObject(getText(url)).optJSONArray("items")?.objects().orEmpty()
        return entries.map { item ->
            val snippet = item.optJSONObject("snippet") ?: JSONObject()
            val title = snippet.optString("title")
            val text = (title + " " + snippet.optString("description")).lowercase()
            Video(youtubeId = item.optString("id"), title = title,
                thumbnail = snippet.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                    ?: "https://img.youtube.com/vi/" + item.optString("id") + "/hqdefault.jpg",
                channel = snippet.optString("channelTitle"), description = snippet.optString("description"),
                durationSec = parseDuration(item.optJSONObject("contentDetails")?.optString("duration").orEmpty()),
                difficulty = when {
                    "beginner" in text || "a1" in text -> "beginner"
                    "advanced" in text || "c1" in text -> "advanced"
                    else -> "intermediate"
                },
                category = when {
                    "interview" in text -> "interviews"; "movie" in text -> "movies"
                    "news" in text -> "news"; "ted" in text -> "ted_talks"
                    else -> "general"
                })
        }
    }

    suspend fun movies(query: String): List<Movie> {
        if (BuildConfig.KINOPOISK_API_KEY.isBlank()) return emptyList()
        val endpoint = if (query.isBlank()) "movie" else "movie/search"
        val url = ("https://api.kinopoisk.dev/v1.4/" + endpoint).toHttpUrl().newBuilder()
            .addQueryParameter("page", "1").addQueryParameter("limit", "30")
        if (query.isNotBlank()) url.addQueryParameter("query", query)
        else { url.addQueryParameter("type", "movie")
            url.addQueryParameter("rating.kp", "7-10")
            url.addQueryParameter("sortField", "votes.kp")
            url.addQueryParameter("sortType", "-1") }
        val j = JSONObject(getText(url.build().toString(),
            mapOf("X-API-KEY" to BuildConfig.KINOPOISK_API_KEY)))
        return j.optJSONArray("docs")?.objects()?.map(Movie::from).orEmpty()
    }


    /** Public-domain Internet Archive film catalog, matching the Flutter ArchiveOrgService. */
    suspend fun archiveVideos(query: String): List<Video> {
        val q = if (query.isBlank())
            "collection:(feature_films) AND mediatype:(movies) AND format:(MPEG4)"
        else "mediatype:(movies) AND format:(MPEG4) AND title:(" +
            query.replace("\"", "") + ")"
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "description")
            .addQueryParameter("sort[]", "downloads desc")
            .addQueryParameter("rows", "30")
            .addQueryParameter("page", "1")
            .addQueryParameter("output", "json").build().toString()
        val docs = JSONObject(getText(url)).optJSONObject("response")
            ?.optJSONArray("docs")?.objects().orEmpty()
        return docs.mapNotNull { item ->
            val id = item.optString("identifier")
            if (id.isBlank()) null else Video(
                youtubeId = id, title = item.optString("title", id),
                thumbnail = "https://archive.org/services/img/" + id,
                description = item.optString("description"),
                sourceType = "archive", category = "movies"
            )
        }
    }

    /** Resolve a playable Internet Archive MP4 and optional external captions. */
    suspend fun resolveArchive(video: Video): Video {
        val id = video.youtubeId
        require(id.isNotBlank()) { "Archive identifier is missing" }
        val files = JSONObject(getText("https://archive.org/metadata/" +
            URLEncoder.encode(id, "UTF-8"))).optJSONArray("files")?.objects().orEmpty()
        val best = files.filter {
            val name = it.optString("name").lowercase()
            name.endsWith(".mp4") || name.endsWith(".m4v")
        }.maxByOrNull { it.optString("size").toLongOrNull() ?: 0 }
            ?: throw IllegalStateException("В архиве нет воспроизводимого MP4")
        val subtitle = files.firstOrNull {
            val name = it.optString("name").lowercase()
            name.endsWith(".srt") || name.endsWith(".vtt")
        }
        fun fileUrl(name: String) = "https://archive.org/download/" + id + "/" +
            URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return video.copy(sourceType = "direct", videoUrl = fileUrl(best.optString("name")),
            subtitleUrl = subtitle?.optString("name")?.let(::fileUrl).orEmpty())
    }

    /** Rivestream is optional: if unavailable, keep the existing movie embed fallback. */
    suspend fun resolveMovie(video: Video): Video {
        val tmdbId = video.youtubeId.toIntOrNull() ?: return video
        if (tmdbId <= 0) return video
        val providers = runCatching {
            JSONObject(getText("https://scrapper.rivestream.org/api/providers"))
                .optJSONArray("data")?.let { j ->
                    (0 until j.length()).mapNotNull { j.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
        }.getOrDefault(emptyList())
        val candidates = listOf("asiacloud", "primevids", "flowcast", "guru") +
            providers.filterNot { it in listOf("asiacloud", "primevids", "flowcast", "guru") }
        for (provider in candidates.distinct().take(8)) {
            if (provider !in providers) continue
            val result = runCatching {
                val url = "https://scrapper.rivestream.org/api/provider".toHttpUrl().newBuilder()
                    .addQueryParameter("provider", provider).addQueryParameter("id", tmdbId.toString())
                    .build().toString()
                JSONObject(getText(url)).optJSONObject("data")
            }.getOrNull() ?: continue
            val candidatesSources = result.optJSONArray("sources")?.objects().orEmpty()
            val badLang = listOf("hindi", "tamil", "telugu", "urdu", "vietnamese", "vietsub")
            val source = candidatesSources.filter {
                val label = it.optString("quality").lowercase()
                it.optString("url").isNotBlank() && badLang.none { lang -> label.contains(lang) }
            }.sortedBy {
                val label = it.optString("quality").lowercase()
                if ("english" in label || "original" in label) 0
                else if ("hls" in label || ".m3u8" in it.optString("url")) 1
                else 2
            }.firstOrNull() ?: continue
            val raw = source.optString("url")
            val parsed = android.net.Uri.parse(raw)
            val resolvedUrl = parsed.getQueryParameter("url") ?: raw
            if (!resolvedUrl.startsWith("https://")) continue
            val upstreamHeaders = parsed.getQueryParameter("headers")?.let {
                runCatching { JSONObject(it) }.getOrNull()
            }
            val referer = upstreamHeaders?.optString("Referer")
                ?.ifBlank { upstreamHeaders.optString("referer") }.orEmpty()
            val captions = result.optJSONArray("captions")?.objects().orEmpty()
            fun subtitle(lang: String): String = captions.firstOrNull {
                val label = it.optString("label", it.optString("lang")).lowercase()
                if (lang == "en") "english" in label || label == "en"
                else "russian" in label || label == "ru"
            }?.let { it.optString("file", it.optString("url")) }.orEmpty()
            return video.copy(sourceType = "direct", videoUrl = resolvedUrl,
                subtitleUrl = subtitle("en"), subtitleUrlRu = subtitle("ru"),
                httpHeaders = if (referer.isBlank()) emptyMap() else mapOf("Referer" to referer))
        }
        return video
    }

    suspend fun lookup(word: String): Lookup {
        val normalized = word.trim()
        var phonetic = ""; var definition = ""; var translation = ""
        runCatching {
            val entries = JSONArray(getText("https://api.dictionaryapi.dev/api/v2/entries/en/" +
                URLEncoder.encode(normalized, "UTF-8")))
            val first = entries.optJSONObject(0) ?: JSONObject()
            phonetic = first.optString("phonetic")
            val phones = first.optJSONArray("phonetics")
            if (phonetic.isEmpty()) phonetic = phones?.objects()
                ?.firstOrNull { it.optString("text").isNotBlank() }?.optString("text").orEmpty()
            val meaning = first.optJSONArray("meanings")?.optJSONObject(0)
            definition = meaning?.optJSONArray("definitions")?.optJSONObject(0)
                ?.optString("definition").orEmpty()
        }
        runCatching {
            val result = JSONObject(getText("https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(normalized, "UTF-8") + "&langpair=en%7Cru"))
            translation = result.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        }
        return Lookup(normalized, phonetic, translation, definition)
    }

    private suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
            headers.forEach { (k, v) -> request.header(k, v) }
            http.newCall(request.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiError(res.code, text.take(200))
                text
            }
        }

    companion object {
        fun parseDuration(s: String): Int {
            val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").find(s) ?: return 0
            return (m.groupValues[1].toIntOrNull() ?: 0) * 3600 +
                (m.groupValues[2].toIntOrNull() ?: 0) * 60 +
                (m.groupValues[3].toIntOrNull() ?: 0)
        }
    }
}
