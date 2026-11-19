package com.cinewords.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant

private val reviewModes = listOf(
    "flashcard" to "Карточки", "multiple_choice" to "Выбор",
    "typing" to "Ввод", "word_builder" to "Буквы"
)

/** Four modes match the Flutter vocabulary review modes and retain Supabase SM-2 ratings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyReviewScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    // Snapshot session order: an SM-2 update must not reshuffle/rebuild the active queue.
    val reviewList = remember {
        val due = state.words.filter { word ->
            word.status != "mastered" &&
                (word.nextReviewAt.isBlank() || runCatching {
                    Instant.parse(word.nextReviewAt).isBefore(Instant.now().plusSeconds(300))
                }.getOrDefault(false))
        }
        if (due.isNotEmpty()) due.take(20)
        else state.words.filter { it.status != "mastered" }.shuffled().take(20)
    }
    var mode by remember { mutableStateOf("flashcard") }
    var position by remember { mutableIntStateOf(0) }
    val card = reviewList.getOrNull(position)
    var flipped by remember(position, mode) { mutableStateOf(false) }
    var selected by remember(position, mode) { mutableStateOf<String?>(null) }
    var typed by remember(position, mode) { mutableStateOf("") }
    var letterIndices by remember(position, mode) { mutableStateOf(listOf<Int>()) }
    var sending by remember { mutableStateOf(false) }
    val options = remember(card?.id) {
        val correct = card?.translation.orEmpty()
        val distractors = state.words.filter { it.id != card?.id && it.translation.isNotBlank() }
            .map { it.translation }.distinct().filter { it != correct }.shuffled().take(3)
        (distractors + correct).distinct().shuffled()
    }
    val targetLetters = remember(card?.id) {
        card?.word?.filter { it.isLetter() }?.lowercase().orEmpty()
    }
    val letters = remember(card?.id) { targetLetters.toList().shuffled() }

    fun submit(quality: Int) {
        val word = card ?: return
        if (sending) return
        sending = true
        scope.launch {
            state.review(word, quality)
            position++
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { state.route = "dictionary" }) { Text("‹ Словарь") }
        Header("Повторение SM-2", position.coerceAtMost(reviewList.size).toString() +
            " / " + reviewList.size)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reviewModes.forEach { (id, title) ->
                FilterChip(selected = mode == id, onClick = { mode = id },
                    label = { Text(title) })
            }
        }
        Spacer(Modifier.height(22.dp))

        if (card == null) {
            Text("Сессия завершена", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { state.route = "dictionary" }) { Text("Готово") }
            return@Column
        }
        when (mode) {
            "multiple_choice" -> {
                Text(card.word, style = MaterialTheme.typography.headlineLarge)
                Text(card.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                if (card.translation.isBlank()) {
                    Text("Для этого слова не задан перевод.")
                    Button(onClick = { submit(0) }, enabled = !sending) { Text("Пропустить") }
                } else {
                    options.forEach { option ->
                        OutlinedButton(onClick = { if (selected == null) selected = option },
                            enabled = selected == null,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(option + if (selected != null && option == card.translation) " ✓" else "")
                        }
                    }
                    if (selected != null) {
                        ReviewFeedback(selected == card.translation, card.translation)
                        Button(onClick = { submit(if (selected == card.translation) 4 else 0) },
                            enabled = !sending) { Text("Следующая") }
                    }
                }
            }
            "typing" -> {
                Text(card.word, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(value = typed,
                    onValueChange = { if (!flipped) typed = it },
                    singleLine = true, enabled = !flipped,
                    label = { Text("Введите перевод") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                if (flipped) {
                    val correct = typed.trim().equals(card.translation.trim(), ignoreCase = true)
                    ReviewFeedback(correct, card.translation)
                    Button(onClick = { submit(if (correct) 5 else 0) },
                        enabled = !sending) { Text("Следующая") }
                } else {
                    Button(onClick = { flipped = true }, enabled = typed.isNotBlank()) { Text("Проверить") }
                }
            }
            "word_builder" -> {
                Text(card.translation.ifBlank { "Составьте слово" },
                    style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                val built = letterIndices.joinToString("") { letters[it].toString() }
                Text(built.ifBlank { "Соберите слово из букв" },
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    letters.forEachIndexed { i, letter ->
                        FilterChip(selected = i in letterIndices,
                            onClick = {
                                if (!flipped) {
                                    letterIndices = if (i in letterIndices) letterIndices - i
                                    else letterIndices + i
                                }
                            },
                            label = { Text(letter.toString()) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (flipped) {
                    val correct = built == targetLetters
                    ReviewFeedback(correct, card.word)
                    Button(onClick = { submit(if (correct) 4 else 0) },
                        enabled = !sending) { Text("Следующая") }
                } else {
                    Row {
                        OutlinedButton(onClick = { letterIndices = emptyList() }) { Text("Сбросить") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { flipped = true },
                            enabled = letterIndices.size == letters.size) { Text("Проверить") }
                    }
                }
            }
            else -> {
                ElevatedCard(Modifier.fillMaxWidth().clickable { flipped = true }) {
                    Column(Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(card.word, style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold)
                        Text(card.phonetic)
                        Spacer(Modifier.height(18.dp))
                        if (card.context.isNotBlank()) Text(card.context)
                        Spacer(Modifier.height(18.dp))
                        Text(if (flipped) card.translation else "Нажмите, чтобы увидеть перевод",
                            style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (!flipped) Button(onClick = { flipped = true }) { Text("Показать ответ") }
                else {
                    Text("Как хорошо вы вспомнили?")
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1 to "Снова", 3 to "Трудно", 4 to "Хорошо", 5 to "Легко")
                            .forEach { (quality, text) ->
                                FilledTonalButton(onClick = { submit(quality) },
                                    enabled = !sending,
                                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text(text) }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewFeedback(correct: Boolean, answer: String) {
    Text(if (correct) "✓ Верно" else "Правильный ответ: " + answer,
        color = if (correct) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(12.dp))
}
