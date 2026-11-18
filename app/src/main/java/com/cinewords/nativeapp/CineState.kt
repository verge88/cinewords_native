package com.cinewords.nativeapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

class CineState(context: Context) {
    val api = CineApi(context.applicationContext)
    private val prefs = context.getSharedPreferences("cinewords_settings", Context.MODE_PRIVATE)

    var route by mutableStateOf("home")
    var selectedVideo by mutableStateOf<Video?>(null)
    var selectedExercise by mutableStateOf("cloze")
    var selectedWord by mutableStateOf<Word?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf("")
    var info by mutableStateOf("")
    var searchText by mutableStateOf("")
    var searchPage by mutableStateOf("")
    var browseTab by mutableStateOf("youtube")
    var theme by mutableStateOf(prefs.getString("preferred_theme_mode", "system") ?: "system")
    val featured = mutableStateListOf<Video>()
    val results = mutableStateListOf<Video>()
    val favorites = mutableStateListOf<Video>()
    val favoriteIds = mutableStateListOf<String>()
    val words = mutableStateListOf<Word>()
    val movies = mutableStateListOf<Movie>()
    var profile by mutableStateOf(Profile())

    fun openVideo(video: Video) { selectedVideo = video; route = "video_detail" }
    fun chooseTheme(mode: String) {
        theme = mode
        prefs.edit().putString("preferred_theme_mode", mode).apply()
    }

    suspend fun loadHome() = safe {
        featured.replace(api.featured())
        if (featured.isEmpty()) {
            val (videos, _) = api.youtube("learn english with subtitles")
            featured.replace(videos)
        }
        reloadFavorites()
    }

    suspend fun reloadFavorites() {
        val ids = api.favoriteIds()
        favoriteIds.replace(ids.toList())
        favorites.replace(api.favorites())
    }

    suspend fun toggleFavorite(video: Video) = safe {
        val isFav = video.id.isNotBlank() && video.id in favoriteIds
        val saved = api.favorite(video, !isFav)
        val index = featured.indexOfFirst { it.youtubeId == saved.youtubeId && it.youtubeId.isNotEmpty() }
        if (index >= 0) featured[index] = saved
        val searchIndex = results.indexOfFirst { it.youtubeId == saved.youtubeId && it.youtubeId.isNotEmpty() }
        if (searchIndex >= 0) results[searchIndex] = saved
        if (selectedVideo?.youtubeId == saved.youtubeId) selectedVideo = saved
        reloadFavorites()
    }

    suspend fun search(query: String = searchText, more: Boolean = false) = safe {
        if (query.isBlank() && browseTab != "favorites" && browseTab != "movies") {
            results.clear()
            return@safe
        }
        searchText = query
        when (browseTab) {
            "youtube" -> {
                val (items, next) = api.youtube(query, if (more) searchPage else "")
                if (!more) results.clear()
                items.forEach { video ->
                    if (results.none { it.youtubeId == video.youtubeId }) results.add(video)
                }
                searchPage = next
            }
            "library" -> results.replace(api.searchLibrary(query))
            "movies" -> movies.replace(api.movies(query))
            "archive" -> results.replace(api.archiveVideos(query))
            "ddgs" -> {
                val page = if (more) searchPage.toIntOrNull() ?: 1 else 1
                val (items, hasMore) = api.ddgs(query, page)
                if (!more) results.clear()
                items.forEach { v ->
                    if (results.none { it.youtubeId.ifBlank { it.videoUrl } ==
                        v.youtubeId.ifBlank { v.videoUrl } }) results.add(v)
                }
                searchPage = if (hasMore) (page + 1).toString() else ""
            }
            "favorites" -> reloadFavorites()
        }
    }

    suspend fun browse(tab: String) {
        browseTab = tab
        searchPage = ""
        when (tab) {
            "favorites" -> safe { reloadFavorites() }
            "movies" -> safe { movies.replace(api.movies("")) }
            "archive" -> safe { results.replace(api.archiveVideos("")) }
            "ddgs" -> { results.clear(); searchPage = "" }
            "youtube" -> safe {
                val (items, next) = api.youtube("learn english conversation")
                results.replace(items)
                searchPage = next
            }
            else -> safe { results.replace(api.category("general")) }
        }
    }

    suspend fun loadWords() = safe { words.replace(api.words()) }
    suspend fun addWord(text: String, tr: String, ph: String = "", context: String = "", type: String = "word") = safe {
        api.addWord(text, tr, ph, context, type)
        words.replace(api.words())
        info = "Сохранено: $text"
    }

    suspend fun deleteWord(word: Word) = safe {
        api.deleteWord(word.id)
        words.removeAll { it.id == word.id }
    }

    suspend fun review(word: Word, quality: Int) = safe {
        api.review(word.id, quality)
        words.replace(api.words())
    }

    suspend fun loadProfile() = safe { profile = api.profile() }

    private suspend inline fun safe(crossinline action: suspend () -> Unit) {
        loading = true
        error = ""
        try { action() } catch (e: Exception) { error = e.message ?: e.toString() }
        finally { loading = false }
    }

    fun reset() {
        route = "home"; featured.clear(); results.clear(); favorites.clear()
        favoriteIds.clear(); words.clear(); profile = Profile()
    }
}

private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.replace(items: Collection<T>) {
    clear()
    addAll(items)
}
