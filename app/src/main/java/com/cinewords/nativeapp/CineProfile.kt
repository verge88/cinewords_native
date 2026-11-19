package com.cinewords.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant

/** Flutter ProfileScreen composition: user card, 2×2 stats, goal ring and settings. */
@Composable
fun CineProfileScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    val p = state.profile
    var goalDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val due = state.words.count { w ->
        w.status != "mastered" && (w.nextReviewAt.isBlank() ||
            runCatching { Instant.parse(w.nextReviewAt).isBefore(Instant.now()) }.getOrDefault(false))
    }
    val progress = (p.today.toFloat() / p.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(50))
                    .background(cs.primary), contentAlignment = Alignment.Center) {
                    Text(p.name.ifBlank { p.email.substringBefore('@') }.take(1).uppercase()
                        .ifBlank { "?" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = cs.onPrimary)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { p.email.substringBefore('@') }.ifBlank { "Профиль" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(p.email, style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Statistics", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CineStatTile(Icons.Default.Bolt, "Новые слова",
                state.words.count { it.status == "new" }.toString(),
                CineColors.Violet, Modifier.weight(1f))
            CineStatTile(Icons.Default.HistoryToggleOff, "К повтору", due.toString(),
                CineColors.Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CineStatTile(Icons.Default.School, "Изучено", p.learned.toString(),
                Color(0xFF66BB6A), Modifier.weight(1f))
            CineStatTile(Icons.Default.Psychology, "В процессе",
                state.words.count { it.status == "new" || it.status == "learning" }.toString(),
                CineColors.Teal, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Daily Goal", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp,
                        color = cs.primary, trackColor = cs.primary.copy(alpha = 0.12f))
                    Text((progress * 100).toInt().toString() + "%",
                        style = MaterialTheme.typography.labelLarge, color = cs.primary,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(p.today.toString() + " / " + p.goal + " min",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Просмотрено всего: " + p.minutes + " мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                    Text("Серия: " + p.streak + " дней",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        CineSettingsItem(Icons.Default.Translate, "Native Language", "Russian") {}
        CineSettingsItem(Icons.Default.Timer, "Daily Goal", p.goal.toString() + " minutes") {
            goalDialog = true
        }
        CineSettingsItem(Icons.Default.Favorite, "My Favorites", "Сохранённые видео") {
            state.route = "catalog"
            scope.launch { state.browse("favorites") }
        }
        CineSettingsItem(Icons.Default.Palette, "App Theme",
            when (state.theme) {
                "dark" -> "Тёмная"
                "light" -> "Светлая"
                "monochrome" -> "Монохромная"
                else -> "Системная"
            }) { themeDialog = true }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { confirmExit = true },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Выйти")
        }
        Spacer(Modifier.height(30.dp))
    }
    if (goalDialog) {
        var goal by remember(p.goal) { mutableFloatStateOf(p.goal.toFloat()) }
        AlertDialog(onDismissRequest = { goalDialog = false },
            title = { Text("Дневная цель") },
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
                            goalDialog = false
                        } catch (e: Exception) { state.error = e.message.orEmpty() }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { goalDialog = false }) { Text("Отмена") }
            })
    }
    if (themeDialog) AlertDialog(onDismissRequest = { themeDialog = false },
        title = { Text("Оформление") },
        text = {
            Column {
                listOf("system" to "Системная", "light" to "Светлая",
                    "dark" to "Тёмная", "monochrome" to "Монохромная").forEach { (id, label) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        state.chooseTheme(id); themeDialog = false
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.theme == id, onClick = {
                            state.chooseTheme(id); themeDialog = false
                        })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Закрыть") } })
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text("Выйти из аккаунта?") },
        text = { Text("Для повторного входа потребуются email и пароль.") },
        confirmButton = { TextButton(onClick = {
            confirmExit = false
            scope.launch { state.api.signOut(); state.reset() }
        }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Отмена") } })
}

@Composable
private fun CineStatTile(icon: ImageVector, title: String, value: String,
    accent: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.25f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun CineSettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}
