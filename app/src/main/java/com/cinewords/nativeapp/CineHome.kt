package com.cinewords.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime

/** Port of the Flutter _HomeFeed composition, not a generic single video list. */
@Composable
fun CineHomeScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    var addVideo by remember { mutableStateOf(false) }
    val due = state.words.count { it.status != "mastered" &&
        (it.nextReviewAt.isBlank() || runCatching {
            Instant.parse(it.nextReviewAt).isBefore(Instant.now())
        }.getOrDefault(false)) }
    val learning = state.words.count { it.status == "new" || it.status == "learning" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            CineHero(state, { addVideo = true }, { state.route = "dictionary" })
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CineQuickTile(Icons.Default.FitnessCenter, "Упражнения", "Тренажёры",
                        CineColors.Violet, Modifier.weight(1f)) { state.route = "exercises" }
                    CineQuickTile(Icons.Default.MenuBook, "Словарь", "Поиск и перевод",
                        CineColors.Teal, Modifier.weight(1f)) { state.route = "dictionary" }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CineQuickTile(Icons.Default.School, "Мои слова",
                        if (learning > 0) "$learning в изучении" else "Повторение",
                        CineColors.Amber, Modifier.weight(1f)) { state.route = "dictionary" }
                    CineQuickTile(Icons.Default.Movie, "Фильмы", "Каталог",
                        CineColors.Coral, Modifier.weight(1f)) {
                        state.route = "movies"
                        scope.launch { state.browse("movies") }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (due > 0) CineDailyPlan(due) { state.route = "review" }
        }
        if (state.favorites.isNotEmpty()) item {
            CineSectionHeader("Продолжить", Icons.Default.PlayCircleOutline) {
                state.route = "catalog"
                scope.launch { state.browse("favorites") }
            }
            CineRail(state.favorites.take(10), state)
            Spacer(Modifier.height(28.dp))
        }
        item {
            CineSectionHeader("Рекомендуем", Icons.Default.AutoAwesome) {
                state.route = "catalog"
                scope.launch { state.browse("youtube") }
            }
            if (state.featured.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("Здесь появятся видео с субтитрами")
                        TextButton(onClick = { addVideo = true }) { Text("Добавить видео") }
                    }
                }
            } else CineRail(state.featured.take(10), state)
            Spacer(Modifier.height(28.dp))
        }
        if (state.trending.isNotEmpty()) {
            item {
                CineSectionHeader("В тренде", Icons.Default.TrendingUp) {
                    state.route = "catalog"
                    scope.launch { state.browse("youtube") }
                }
            }
            items(state.trending.take(5)) { video ->
                CineVideoCard(video, { state.openVideo(video) },
                    video.id in state.favoriteIds,
                    { scope.launch { state.toggleFavorite(video) } },
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            OutlinedCard(onClick = { addVideo = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircleOutline, null,
                        tint = CineColors.Violet, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Добавить своё видео", style = MaterialTheme.typography.titleMedium)
                        Text("Ссылка YouTube или прямой адрес",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    if (addVideo) {
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addVideo = false },
            title = { Text("Добавить видео") },
            text = { OutlinedTextField(value = url, onValueChange = { url = it },
                singleLine = true, label = { Text("Ссылка на видео") }) },
            confirmButton = {
                TextButton(enabled = url.isNotBlank(), onClick = {
                    val source = url.trim()
                    val id = extractYouTubeId(source)
                    val entry = if (id.matches(Regex("^[A-Za-z0-9_-]{11}$")))
                        Video(youtubeId = id, title = "YouTube · " + id)
                    else Video(title = source.substringAfterLast('/'), videoUrl = source,
                        sourceType = "direct")
                    state.openVideo(entry)
                    addVideo = false
                }) { Text("Открыть") }
            },
            dismissButton = { TextButton(onClick = { addVideo = false }) { Text("Отмена") } })
    }
}

@Composable
private fun CineHero(state: CineState, onAdd: () -> Unit, onDictionary: () -> Unit) {
    val p = state.profile
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val start = if (dark) CineColors.HeroDark else MaterialTheme.colorScheme.primaryContainer
    val end = if (dark) CineColors.HeroDarkAlt else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    val muted = fg.copy(alpha = 0.65f)
    val border = fg.copy(alpha = 0.09f)
    val fraction = (p.today.toFloat() / p.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val name = p.name.ifBlank { p.email.substringBefore("@") }.ifBlank { "Гость" }
    val greeting = when (LocalTime.now().hour) {
        in 0..4 -> "Доброй ночи"
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        else -> "Добрый вечер"
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(start, end)))
        .border(1.dp, border, RoundedCornerShape(28.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(CineColors.Violet.copy(alpha = 0.18f))
                .clickable { state.route = "profile" }, contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
                    color = CineColors.Violet)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(greeting, style = MaterialTheme.typography.bodySmall, color = muted)
                Text(name, color = fg, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            CineHeroAction(Icons.Default.Search, fg, onDictionary)
            Spacer(Modifier.width(8.dp))
            CineHeroAction(Icons.Default.Add, fg, onAdd)
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp, color = CineColors.Violet,
                    trackColor = fg.copy(alpha = 0.12f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.today.toString(), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = fg)
                    Text("мин", color = muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(if (fraction >= 1f) "Цель дня выполнена" else "Цель дня · " + p.goal + " мин",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = fg)
                Spacer(Modifier.height(4.dp))
                Text(when {
                    fraction >= 1f -> "Отличный результат, так держать"
                    p.today == 0 -> "Начните с короткого видео"
                    else -> "Осталось " + (p.goal - p.today).coerceAtLeast(0) + " мин"
                }, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = border)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CineHeroStat(Icons.Default.LocalFireDepartment, CineColors.Coral,
                p.streak.toString(), "дней подряд", fg, muted, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(50.dp).background(border))
            CineHeroStat(Icons.Default.CheckCircleOutline, CineColors.Teal,
                p.learned.toString(), "слов изучено", fg, muted, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CineHeroAction(icon: ImageVector, color: Color, action: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
        .background(color.copy(alpha = 0.10f)).clickable(onClick = action),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CineHeroStat(icon: ImageVector, accent: Color, value: String, caption: String,
    fg: Color, muted: Color, modifier: Modifier) {
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, color = fg, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(caption, color = muted, style = MaterialTheme.typography.labelSmall,
                maxLines = 2)
        }
    }
}

@Composable
private fun CineQuickTile(icon: ImageVector, title: String, subtitle: String, accent: Color,
    modifier: Modifier, action: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(onClick = action, modifier = modifier.heightIn(min = 114.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowOutward, null, modifier = Modifier.size(16.dp),
                    tint = cs.onSurfaceVariant.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CineDailyPlan(due: Int, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), Color(0xFF8E7BFF))))
        .clickable(onClick = action).padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("ПЛАН НА СЕГОДНЯ", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f),
                letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            Text("$due слов к повторению", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Займёт около 5 минут", style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f))
        }
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(30.dp), tint = Color.White)
        }
    }
}

@Composable
private fun CineSectionHeader(title: String, icon: ImageVector, action: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
            .background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        TextButton(onClick = action) { Text("Все") }
    }
}

@Composable
private fun CineRail(videos: List<Video>, state: CineState) {
    val scope = rememberCoroutineScope()
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos) { v ->
            CineVideoCard(v, { state.openVideo(v) }, v.id in state.favoriteIds,
                { scope.launch { state.toggleFavorite(v) } },
                modifier = Modifier.width(272.dp), compact = true)
        }
    }
}

/** Full-bleed 16:9 Flutter VideoCard with difficulty, duration and favorite overlays. */
@Composable
fun CineVideoCard(video: Video, onClick: () -> Unit, favorite: Boolean,
    onFavorite: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val level = video.difficulty.lowercase()
    val label = when (level) {
        "beginner" -> "Начальный"
        "elementary" -> "Базовый"
        "advanced" -> "Продвинутый"
        else -> "Средний"
    }
    val accent = when (level) {
        "beginner", "elementary" -> CineColors.Teal
        "advanced" -> CineColors.Coral
        else -> CineColors.Violet
    }
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.3f))) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
                val thumb = video.thumbnail.ifBlank {
                    if (video.youtubeId.matches(Regex("^[A-Za-z0-9_-]{11}$")))
                        "https://img.youtube.com/vi/" + video.youtubeId + "/hqdefault.jpg"
                    else ""
                }
                AsyncImage(model = thumb.ifBlank { null }, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(accent)
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
                if (video.durationSec > 0) {
                    val d = video.durationSec
                    Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (d >= 3600) "%d:%02d:%02d".format(d / 3600, d / 60 % 60, d % 60)
                            else "%d:%02d".format(d / 60, d % 60),
                            color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)
                    .padding(4.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.25f))) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Избранное", tint = if (favorite) Color(0xFFFF6B6B) else Color.White)
                }
            }
            Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
                Text(video.title, style = if (compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(14.dp),
                        tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(video.channel.ifBlank { video.category },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.Subtitles, null, modifier = Modifier.size(14.dp),
                            tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
