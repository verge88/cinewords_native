package com.cinewords.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
    val movieTab = state.route == "movies"
    val tabs = if (movieTab) listOf(
        "movies" to "Kinopoisk", "archive" to "Archive.org", "ddgs" to "Видео"
    ) else listOf(
        "youtube" to "YouTube", "library" to "Медиатека", "favorites" to "Избранное"
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header(if (movieTab) "Фильмы" else "Видео",
            if (movieTab) "Выберите фильм с субтитрами" else "Английский из видео и диалогов")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { (id, label) ->
                FilterChip(selected = state.browseTab == id,
                    onClick = { scope.launch { state.browse(id) } },
                    label = { Text(label) },
                    leadingIcon = if (state.browseTab == id) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.browseTab != "favorites") {
            OutlinedTextField(value = state.searchText,
                onValueChange = { state.searchText = it },
                placeholder = { Text(if (movieTab) "Поиск фильмов..." else "Найти видео...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { scope.launch { state.search() } }) {
                        Icon(Icons.Default.ArrowForward, "Поиск")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp))
            Spacer(Modifier.height(12.dp))
        }
        if (state.browseTab == "movies" && BuildConfig.KINOPOISK_API_KEY.isBlank()) {
            Text("Для Kinopoisk укажите API-ключ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.browseTab == "youtube" && BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            Text("Поиск YouTube без ключа ограничен медиатекой",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.browseTab == "youtube" && state.searchText.startsWith("http")) {
            TextButton(onClick = {
                val url = state.searchText.trim()
                val id = extractYouTubeId(url)
                state.openVideo(if (id.matches(Regex("^[A-Za-z0-9_-]{11}$")))
                    Video(youtubeId = id, title = url)
                else Video(title = url.substringAfterLast('/'), sourceType = "direct", videoUrl = url))
            }) { Text("Открыть ссылку напрямую") }
        }
        if (state.browseTab == "movies") {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
                items(state.movies) { movie ->
                    CineMoviePoster(movie) { state.openVideo(movie.toVideo()) }
                }
            }
        } else {
            val videos = if (state.browseTab == "favorites") state.favorites else state.results
            LazyColumn(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
                items(videos) { video ->
                    CineVideoCard(video, { state.openVideo(video) },
                        video.id in state.favoriteIds,
                        { scope.launch { state.toggleFavorite(video) } },
                        modifier = Modifier.fillMaxWidth())
                }
                if (state.browseTab in listOf("youtube", "ddgs") && state.searchPage.isNotBlank()) {
                    item {
                        FilledTonalButton(onClick = {
                            scope.launch { state.search(more = true) }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить ещё") }
                    }
                }
                if (videos.isEmpty() && !state.loading) item {
                    Text("Пока ничего не найдено", modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CineMoviePoster(movie: Movie, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                AsyncImage(model = movie.image, contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (movie.rating > 0) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text("★ " + "%.1f".format(movie.rating),
                        color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(movie.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(movie.year, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VideoTile(video: Video, onClick: () -> Unit, favorite: Boolean, onFavorite: () -> Unit) {
    CineVideoCard(video, onClick, favorite, onFavorite, modifier = Modifier.fillMaxWidth())
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
        Button(onClick = {
            scope.launch {
                try {
                    state.selectedVideo = when (video.sourceType) {
                        "archive" -> state.api.resolveArchive(video)
                        "vidapi" -> state.api.resolveMovie(video)
                        else -> video
                    }
                    state.route = "player"
                } catch (e: Exception) { state.error = e.message.orEmpty() }
            }
        }, modifier = Modifier.fillMaxWidth()) {
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
    var type by remember { mutableStateOf("word") }
    var tab by remember { mutableStateOf("all") }
    var addDialog by remember { mutableStateOf(false) }
    val list = state.words.filter { it.type == type }
    val shown = list.filter { card ->
        val matchTab = when (tab) {
            "new" -> card.status == "new"
            "learning" -> card.status in listOf("learning", "review")
            "mastered" -> card.status == "mastered"
            else -> true
        }
        matchTab && (query.isBlank() || card.word.contains(query, ignoreCase = true) ||
            card.translation.contains(query, ignoreCase = true))
    }
    val due = state.words.count { it.status != "mastered" &&
        (it.nextReviewAt.isBlank() || runCatching {
            Instant.parse(it.nextReviewAt).isBefore(Instant.now())
        }.getOrDefault(false)) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vocabulary", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { state.route = "exercises" }) {
                    Icon(Icons.Default.FitnessCenter, "Упражнения")
                }
                IconButton(onClick = { addDialog = true }) {
                    Icon(Icons.Default.MenuBook, "Добавить слово")
                }
                FilledTonalButton(onClick = { state.route = "review" }) {
                    Icon(Icons.Default.School, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (due > 0) "Review ($due)" else "Practice")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("New", list.count { it.status == "new" }, CineColors.Coral),
                    Triple("Learning", list.count { it.status == "learning" }, CineColors.Violet),
                    Triple("Review", list.count { it.status == "review" }, CineColors.Amber),
                    Triple("Mastered", list.count { it.status == "mastered" }, CineColors.Teal)
                ).forEach { (label, count, color) ->
                    Card(colors = CardDefaults.cardColors(
                        containerColor = color.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.width(85.dp).padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(count.toString(), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = color)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = color, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "word", onClick = { type = "word" },
                    label = { Text("Words") },
                    leadingIcon = { Icon(Icons.Default.Abc, null, modifier = Modifier.size(18.dp)) })
                FilterChip(selected = type == "phrase", onClick = { type = "phrase" },
                    label = { Text("Phrases") },
                    leadingIcon = { Icon(Icons.Default.FormatQuote, null, modifier = Modifier.size(18.dp)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("all" to "All", "new" to "New", "learning" to "Learning",
                    "mastered" to "Mastered").forEach { (id, label) ->
                    TextButton(onClick = { tab = id }) {
                        Text(label, fontWeight = if (tab == id) FontWeight.Bold
                            else FontWeight.Normal,
                            color = if (tab == id) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedTextField(value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Поиск в словаре") },
                singleLine = true, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 95.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown) { word ->
                    Card(onClick = {
                        state.selectedWord = word
                        state.route = "word_detail"
                    }, shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center) {
                                Text(word.word.take(1).uppercase(), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(word.word, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text(word.translation, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(word.status.replaceFirstChar { it.uppercaseChar() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CineColors.Violet)
                            }
                            Icon(Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (shown.isEmpty()) item {
                    Text("Слов здесь пока нет", modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FloatingActionButton(onClick = { addDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.Add, "Добавить слово")
        }
    }
    if (addDialog) AddWordDialog(state, onDismiss = { addDialog = false })
}

@Composable
fun AddWordDialog(state: CineState, onDismiss: () -> Unit, defaultWord: String = "",
    context: String = "") {
    val scope = rememberCoroutineScope()
    val speech = rememberSpeech()
    var word by remember(defaultWord) { mutableStateOf(defaultWord) }
    var translation by remember(defaultWord) { mutableStateOf("") }
    var phonetic by remember(defaultWord) { mutableStateOf("") }
    var definition by remember(defaultWord) { mutableStateOf("") }
    var type by remember { mutableStateOf("word") }
    var busy by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf("") }

    suspend fun fetchLookup(text: String) {
        busy = true
        lookupError = ""
        try {
            val found = state.api.lookup(text.trim())
            // Never overwrite the user's typed translation when a late result arrives.
            if (word.trim().equals(text.trim(), ignoreCase = true)) {
                if (translation.isBlank()) translation = found.translation
                if (phonetic.isBlank()) phonetic = found.phonetic
                definition = found.definition
            }
        } catch (e: Exception) {
            lookupError = e.message ?: "Словарь недоступен"
        } finally { busy = false }
    }

    // Tapping an English word in a transcript or reader should open its definition.
    LaunchedEffect(defaultWord) {
        if (defaultWord.isNotBlank()) fetchLookup(defaultWord)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Добавить в словарь") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("Слово / фраза") })
            if (definition.isNotBlank()) Text(definition, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = translation, onValueChange = { translation = it },
                label = { Text("Перевод") })
            OutlinedTextField(value = phonetic, onValueChange = { phonetic = it },
                label = { Text("Транскрипция") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = word.isNotBlank() && !busy,
                    onClick = { scope.launch { fetchLookup(word) } }) {
                    Text(if (busy) "Поиск…" else "Обновить перевод")
                }
                IconButton(enabled = word.isNotBlank(), onClick = { speech.speak(word) }) {
                    Icon(Icons.Default.VolumeUp, "Произнести")
                }
            }
            if (lookupError.isNotBlank()) Text(lookupError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
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
    VocabularyReviewScreen(state)
}

@Composable
fun ProfileScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val p = state.profile
    var editGoal by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
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
        TextButton(onClick = { editGoal = true }) { Text("Изменить дневную цель: " + p.goal + " мин") }
        Spacer(Modifier.height(20.dp))
        Text("Оформление", style = MaterialTheme.typography.titleLarge)
        listOf("system" to "Системная", "light" to "Светлая",
            "dark" to "Тёмная", "monochrome" to "Монохромная").forEach { (id, title) ->
            Row(Modifier.fillMaxWidth().clickable { state.chooseTheme(id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.theme == id, onClick = { state.chooseTheme(id) })
                Text(title)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            state.route = "catalog"
            scope.launch { state.browse("favorites") }
        }) { Text("Избранное") }
        OutlinedButton(onClick = { confirmSignOut = true }) { Text("Выйти") }
    }
    if (editGoal) {
        var goal by remember(p.goal) { mutableFloatStateOf(p.goal.toFloat()) }
        AlertDialog(onDismissRequest = { editGoal = false }, title = { Text("Дневная цель") },
            text = {
                Column {
                    Text(goal.toInt().toString() + " минут в день")
                    Slider(value = goal, onValueChange = { goal = it },
                        valueRange = 5f..120f, steps = 22)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            state.api.setDailyGoal(goal.toInt())
                            state.loadProfile()
                            editGoal = false
                        } catch (e: Exception) { state.error = e.message.orEmpty() }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editGoal = false }) { Text("Отмена") }
            })
    }
    if (confirmSignOut) {
        AlertDialog(onDismissRequest = { confirmSignOut = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Для повторного входа потребуются email и пароль.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { state.api.signOut(); state.reset() }
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Отмена") }
            })
    }
}
