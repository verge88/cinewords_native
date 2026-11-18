package com.cinewords.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun Header(title: String, subtitle: String = "") {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HomeScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val categories = listOf(
        "movies" to "Фильмы", "series" to "Сериалы", "ted_talks" to "TED",
        "news" to "Новости", "interviews" to "Интервью", "music" to "Музыка",
        "cartoons" to "Мультфильмы"
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Header("CineWords", "Смотрите, слушайте и запоминайте")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().clickable { state.route = "dictionary" }) {
                Column(Modifier.padding(20.dp)) {
                    Text("Ваш прогресс", style = MaterialTheme.typography.titleLarge)
                    Text(state.words.size.toString() + " слов в словаре · " +
                        state.profile.streak + " дней подряд")
                    Spacer(Modifier.height(12.dp))
                    Text("Практиковаться", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Text("Категории", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (id, title) ->
                    AssistChip(onClick = {
                        state.route = "catalog"
                        scope.launch {
                            state.browse("library")
                            state.results.clear()
                            try { state.results.addAll(state.api.category(id)) }
                            catch (e: Exception) { state.error = e.message.orEmpty() }
                        }
                    }, label = { Text(title) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Рекомендуемые видео", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { state.loadHome() } }) { Text("Обновить") }
            }
        }
        if (state.featured.isEmpty()) item {
            Text("Видео не найдены. Проверьте соединение и настройку API-ключа YouTube.")
        }
        items(state.featured) { video ->
            VideoTile(video, onClick = { state.openVideo(video) },
                favorite = video.id in state.favoriteIds,
                onFavorite = { scope.launch { state.toggleFavorite(video) } })
        }
    }
}

@Composable
fun CatalogScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Каталог", "Видео с субтитрами и фильмы")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("youtube" to "YouTube", "library" to "Медиатека",
                "movies" to "Фильмы", "favorites" to "Избранное").forEach { (id, label) ->
                FilterChip(selected = state.browseTab == id,
                    onClick = { scope.launch { state.browse(id) } }, label = { Text(label) })
            }
        }
        if (state.browseTab != "favorites") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.searchText, onValueChange = { state.searchText = it },
                    placeholder = { Text("Поиск или ссылка на видео") },
                    singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { state.search() } }) {
                    Icon(Icons.Default.Search, "Найти")
                }
            }
        }
        if (state.browseTab == "movies" && BuildConfig.KINOPOISK_API_KEY.isBlank())
            Text("Для поиска фильмов укажите KINOPOISK_API_KEY в local.properties.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.browseTab == "youtube" && BuildConfig.YOUTUBE_API_KEY.isBlank())
            Text("YouTube API не настроен: доступен поиск по сохранённым видео.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.browseTab == "youtube" && state.searchText.startsWith("http")) {
            OutlinedButton(onClick = {
                val url = state.searchText.trim()
                val ytId = extractYouTubeId(url)
                state.openVideo(if (ytId.matches(Regex("^[A-Za-z0-9_-]{11}$")))
                    Video(youtubeId = ytId, title = url)
                else Video(title = url.substringAfterLast('/'), sourceType = "direct", videoUrl = url))
            }) { Text("Открыть ссылку напрямую") }
        }
        when (state.browseTab) {
            "movies" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                items(state.movies) { movie -> MovieTile(movie) { state.openVideo(movie.toVideo()) } }
            }
            else -> {
                val data = if (state.browseTab == "favorites") state.favorites else state.results
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(data) { video ->
                        VideoTile(video, { state.openVideo(video) }, video.id in state.favoriteIds,
                            { scope.launch { state.toggleFavorite(video) } })
                    }
                    if (state.browseTab == "youtube" && state.searchPage.isNotBlank())
                        item {
                            Button(onClick = { scope.launch { state.search(more = true) } },
                                modifier = Modifier.fillMaxWidth()) { Text("Загрузить ещё") }
                        }
                }
            }
        }
    }
}

@Composable
fun VideoTile(video: Video, onClick: () -> Unit, favorite: Boolean, onFavorite: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = video.thumbnail.ifBlank {
                if (video.youtubeId.isNotBlank()) "https://img.youtube.com/vi/" +
                    video.youtubeId + "/hqdefault.jpg" else null
            }, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 116.dp, height = 78.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text(video.channel.ifBlank { video.category }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (video.durationSec > 0)
                    Text((video.durationSec / 60).toString() + ":" +
                        (video.durationSec % 60).toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное",
                    tint = if (favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MovieTile(movie: Movie, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = movie.image, contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 74.dp, height = 105.dp))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(movie.title, fontWeight = FontWeight.Bold)
                Text(movie.year + " · ⭐ " + "%.1f".format(movie.rating))
                Text(movie.description, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun VideoDetailScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val video = state.selectedVideo ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = { state.route = "catalog" }) { Text("‹ Назад") }
        AsyncImage(model = video.thumbnail, contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(16.dp))
        Text(video.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { state.route = "player" }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Смотреть")
        }
        OutlinedButton(onClick = { scope.launch { state.toggleFavorite(video) } },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FavoriteBorder, null)
            Text("Избранное")
        }
        Spacer(Modifier.height(16.dp))
        Text(video.description)
    }
}

@Composable
fun DictionaryScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var addDialog by remember { mutableStateOf(false) }
    val due = state.words.count { it.status != "mastered" &&
        (it.nextReviewAt.isBlank() ||
            runCatching { Instant.parse(it.nextReviewAt).isBefore(Instant.now().plusSeconds(300)) }.getOrDefault(false)) }
    val filtered = state.words.filter {
        (filter == "all" || it.type == filter || it.status == filter) &&
            (query.isBlank() || it.word.contains(query, true) || it.translation.contains(query, true))
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Header("Мой словарь", state.words.size.toString() + " карточек · " + due + " к повторению")
            Button(onClick = { state.route = "review" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Style, null); Text(" Интервальное повторение")
            }
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Найти слово") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "Все", "word" to "Слова", "phrase" to "Фразы",
                    "new" to "Новые", "learning" to "Учу", "mastered" to "Выучены").forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { word ->
                    Card(Modifier.fillMaxWidth().clickable {
                        state.selectedWord = word
                        state.route = "word_detail"
                    }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(word.word, fontWeight = FontWeight.Bold)
                                Text(word.translation, style = MaterialTheme.typography.bodyMedium)
                                Text(word.status + " · " + word.type,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { scope.launch { state.deleteWord(word) } }) {
                                Icon(Icons.Default.DeleteOutline, "Удалить")
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { addDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (addDialog) AddWordDialog(state, onDismiss = { addDialog = false })
}

@Composable
fun AddWordDialog(state: CineState, onDismiss: () -> Unit, defaultWord: String = "",
    context: String = "") {
    val scope = rememberCoroutineScope()
    var word by remember(defaultWord) { mutableStateOf(defaultWord) }
    var translation by remember { mutableStateOf("") }
    var phonetic by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("word") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Добавить в словарь") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("Слово / фраза") })
            OutlinedTextField(value = translation, onValueChange = { translation = it },
                label = { Text("Перевод") })
            OutlinedTextField(value = phonetic, onValueChange = { phonetic = it },
                label = { Text("Транскрипция") })
            TextButton(enabled = word.isNotBlank() && !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val found = state.api.lookup(word)
                        translation = found.translation
                        phonetic = found.phonetic
                    } catch (e: Exception) { state.error = e.message.orEmpty() }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Поиск…" else "Найти перевод и транскрипцию") }
            Row {
                FilterChip(selected = type == "word", onClick = { type = "word" },
                    label = { Text("Слово") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = type == "phrase", onClick = { type = "phrase" },
                    label = { Text("Фраза") })
            }
        }
    }, confirmButton = {
        TextButton(enabled = word.isNotBlank() && !busy, onClick = {
            scope.launch { state.addWord(word, translation, phonetic, context, type); onDismiss() }
        }) { Text("Сохранить") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun WordDetailScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val word = state.selectedWord ?: return
    var lookup by remember(word.id) { mutableStateOf<Lookup?>(null) }
    LaunchedEffect(word.id) { lookup = runCatching { state.api.lookup(word.word) }.getOrNull() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = { state.route = "dictionary" }) { Text("‹ Назад") }
        Text(word.word, style = MaterialTheme.typography.headlineLarge)
        Text(word.phonetic.ifBlank { lookup?.phonetic.orEmpty() })
        Text(word.translation, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(lookup?.definition.orEmpty())
        Text(word.context, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Повторений: " + word.repetitionCount + " · " + word.status)
        Button(onClick = { scope.launch { state.review(word, 4); state.route = "dictionary" } }) {
            Text("Знаю это слово")
        }
        OutlinedButton(onClick = { scope.launch { state.deleteWord(word); state.route = "dictionary" } }) {
            Text("Удалить")
        }
    }
}

@Composable
fun ReviewScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val reviewList = remember(state.words.toList()) {
        val due = state.words.filter {
            it.status != "mastered" && (it.nextReviewAt.isBlank() ||
                runCatching { Instant.parse(it.nextReviewAt).isBefore(Instant.now().plusSeconds(300)) }
                    .getOrDefault(false))
        }
        if (due.isNotEmpty()) due else state.words.filter { it.status != "mastered" }.shuffled().take(20)
    }
    var position by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    val word = reviewList.getOrNull(position)
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { state.route = "dictionary" }) { Text("‹ Словарь") }
        Header("Повторение SM-2", position.coerceAtMost(reviewList.size).toString() +
            " / " + reviewList.size)
        Spacer(Modifier.weight(1f))
        if (word == null) {
            Text("Сессия завершена", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { state.route = "dictionary" }) { Text("Готово") }
        } else {
            ElevatedCard(Modifier.fillMaxWidth().clickable { showAnswer = true }) {
                Column(Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(word.word, style = MaterialTheme.typography.headlineLarge)
                    Text(word.phonetic)
                    Spacer(Modifier.height(22.dp))
                    if (showAnswer) {
                        Text(word.translation, style = MaterialTheme.typography.titleLarge)
                        Text(word.context)
                    } else Text("Нажмите для ответа")
                }
            }
            Spacer(Modifier.height(24.dp))
            if (!showAnswer) Button(onClick = { showAnswer = true }) { Text("Показать ответ") }
            else {
                Text("Как хорошо вы вспомнили?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Снова", 3 to "Трудно", 4 to "Хорошо", 5 to "Легко")
                        .forEach { (quality, label) ->
                            FilledTonalButton(onClick = {
                                scope.launch { state.review(word, quality) }
                                position++; showAnswer = false
                            }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(label) }
                        }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun ProfileScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val p = state.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header("Профиль", p.email)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall)
                Text("🔥 " + p.streak + " дней подряд")
                Spacer(Modifier.height(8.dp))
                Text("Сегодня: " + p.today + " / " + p.goal + " минут")
                LinearProgressIndicator(progress = {
                    (p.today.toFloat() / p.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Статистика", style = MaterialTheme.typography.titleLarge)
        Text("Выучено слов: " + p.learned)
        Text("Просмотрено минут: " + p.minutes)
        Text("Карточек в словаре: " + state.words.size)
        Spacer(Modifier.height(20.dp))
        Text("Оформление", style = MaterialTheme.typography.titleLarge)
        listOf("system" to "Системная", "light" to "Светлая",
            "dark" to "Тёмная", "monochrome" to "Монохромная").forEach { (id, title) ->
            Row(Modifier.fillMaxWidth().clickable { state.setTheme(id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.theme == id, onClick = { state.setTheme(id) })
                Text(title)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            state.route = "catalog"
            scope.launch { state.browse("favorites") }
        }) { Text("Избранное") }
        OutlinedButton(onClick = {
            scope.launch { state.api.signOut(); state.reset() }
        }) { Text("Выйти") }
    }
}
