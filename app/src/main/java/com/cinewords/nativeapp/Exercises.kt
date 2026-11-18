package com.cinewords.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private data class Exercise(val id: String, val name: String, val description: String)
private val exercises = listOf(
    Exercise("review", "Интервальное повторение", "Карточки с SM-2"),
    Exercise("clusters", "Семантические кластеры", "Группировка по темам"),
    Exercise("definition", "Определение → слово", "Угадай по словарному определению"),
    Exercise("shadow", "Теневое повторение", "Повторяй английские фразы"),
    Exercise("dictation", "Диктант с пробелами", "Слушай и вставляй слова"),
    Exercise("pairs", "Минимальные пары", "Тренировка фонематического слуха"),
    Exercise("builder", "Конструктор предложений", "Расставь слова по порядку"),
    Exercise("tense", "Трансформация времён", "Меняй время глаголов"),
    Exercise("cloze", "Заполни пропуск", "Выбери правильный вариант"),
    Exercise("reader", "Читалка с тапом на слово", "Перевод и добавление в словарь"),
    Exercise("invisible", "Слово-невидимка", "Восстанови исчезнувшие слова")
)

@Composable
fun ExercisesHub(state: CineState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header("Упражнения", "11 способов закрепить английский")
        exercises.forEach { exercise ->
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable {
                if (exercise.id == "review") state.route = "review"
                else { state.selectedExercise = exercise.id; state.route = "exercise" }
            }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, fontWeight = FontWeight.Bold)
                        Text(exercise.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
fun ExerciseScreen(state: CineState) {
    val id = state.selectedExercise
    val title = exercises.find { it.id == id }?.name.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = { state.route = "exercises" }) { Text("‹ Упражнения") }
        Header(title)
        key(id) {
            when (id) {
                "cloze" -> ClozeExercise()
                "clusters" -> ClusterExercise()
                "definition" -> DefinitionExercise(state)
                "shadow" -> ShadowExercise()
                "dictation" -> DictationExercise()
                "pairs" -> MinimalPairsExercise()
                "builder" -> BuilderExercise()
                "tense" -> TenseExercise()
                "reader" -> ReaderExercise(state)
                "invisible" -> InvisibleExercise()
            }
        }
    }
}

private data class Cloze(val sentence: String, val options: List<String>, val answer: Int)
private val clozes = listOf(
    Cloze("She ___ to school every day.", listOf("go", "goes", "going", "went"), 1),
    Cloze("I ___ a movie last night.", listOf("watch", "watched", "watching", "watches"), 1),
    Cloze("They have ___ in London for ten years.", listOf("live", "lives", "lived", "living"), 2),
    Cloze("There ___ many books on the shelf.", listOf("is", "are", "be", "was"), 1),
    Cloze("If it rains, I ___ stay home.", listOf("will", "would", "am", "do"), 0),
    Cloze("He is the ___ student in the class.", listOf("good", "better", "best", "goodest"), 2),
    Cloze("I look forward ___ hearing from you.", listOf("to", "for", "at", "on"), 0),
    Cloze("She ___ never been to Paris.", listOf("have", "has", "had", "is"), 1),
    Cloze("Could you ___ me with this box?", listOf("help", "helping", "helps", "helped"), 0),
    Cloze("The book ___ I read was amazing.", listOf("who", "which", "where", "whose"), 1)
)

@Composable
private fun ClozeExercise() {
    var index by remember { mutableIntStateOf(0) }
    var answer by remember { mutableIntStateOf(-1) }
    var streak by remember { mutableIntStateOf(0) }
    val item = clozes[index % clozes.size]
    Text("🔥 Серия: " + streak)
    Spacer(Modifier.height(24.dp))
    Text(item.sentence.replace("___", if (answer < 0) "______" else item.options[answer]),
        style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(24.dp))
    item.options.forEachIndexed { i, option ->
        OutlinedButton(onClick = { if (answer < 0) {
            answer = i; streak = if (i == item.answer) streak + 1 else 0
        } }, enabled = answer < 0, modifier = Modifier.fillMaxWidth()) {
            Text(option + if (answer >= 0 && i == item.answer) " ✓" else "")
        }
    }
    if (answer >= 0) {
        Feedback(answer == item.answer, item.options[item.answer])
        Button(onClick = { index++; answer = -1 }) { Text("Следующее") }
    }
}

private val groups = listOf(
    mapOf("food" to listOf("apple", "bread", "cheese", "soup", "pasta"),
        "transport" to listOf("car", "bus", "train", "bicycle", "plane"),
        "emotions" to listOf("happy", "sad", "angry", "proud", "afraid")),
    mapOf("house" to listOf("kitchen", "bedroom", "window", "floor", "roof"),
        "nature" to listOf("tree", "river", "mountain", "beach", "forest"),
        "work" to listOf("meeting", "salary", "office", "colleague", "deadline")),
    mapOf("animals" to listOf("dog", "lion", "eagle", "wolf", "rabbit"),
        "body" to listOf("finger", "shoulder", "knee", "elbow", "tongue"),
        "time" to listOf("morning", "tonight", "yesterday", "minute", "century"))
)

@Composable
private fun ClusterExercise() {
    var round by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var guess by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    val data = groups[round % groups.size]
    val words = remember(round) { data.flatMap { (cat, items) -> items.map { it to cat } }.shuffled() }
    val pair = words.getOrNull(index)
    if (pair == null) {
        Text("Раунд завершён: " + score + " / " + words.size)
        Button(onClick = { round++; index = 0; guess = ""; score = 0 }) { Text("Новый раунд") }
        return
    }
    Text("Слово " + (index + 1) + " из " + words.size)
    Spacer(Modifier.height(20.dp))
    Text(pair.first, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(20.dp))
    data.keys.forEach { category ->
        OutlinedButton(onClick = {
            if (guess.isBlank()) { guess = category; if (category == pair.second) score++ }
        }, modifier = Modifier.fillMaxWidth(), enabled = guess.isBlank()) { Text(category) }
    }
    if (guess.isNotBlank()) {
        Feedback(guess == pair.second, pair.second)
        Button(onClick = { index++; guess = "" }) { Text("Далее") }
    }
}

@Composable
private fun DefinitionExercise(state: CineState) {
    val words = remember(state.words.toList()) {
        state.words.filter { it.type == "word" && it.word.matches(Regex("[A-Za-z-]+")) }
            .map { it.word }.ifEmpty { listOf("apple", "mountain", "journey", "courage", "beautiful") }
    }
    var index by remember { mutableIntStateOf(0) }
    val answer = words[index % words.size]
    var definition by remember(answer) { mutableStateOf("") }
    var input by remember(answer) { mutableStateOf("") }
    var checked by remember(answer) { mutableStateOf(false) }
    LaunchedEffect(answer) {
        definition = runCatching { state.api.lookup(answer).definition }.getOrDefault("")
            .ifBlank { "Угадайте слово из вашего словаря по его переводу" }
    }
    Text(definition, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(value = input, onValueChange = { input = it },
        label = { Text("Слово на английском") }, modifier = Modifier.fillMaxWidth())
    if (checked) Feedback(input.trim().equals(answer, true), answer)
    Button(onClick = { if (!checked) checked = true else index++ }) {
        Text(if (checked) "Следующее" else "Проверить")
    }
}

private val shadowPhrases = listOf(
    "Could you please repeat that more slowly?", "I would like a cup of tea, please.",
    "How much does this cost?", "Where is the nearest bus stop?",
    "It was nice meeting you today.", "Thank you very much for your help.",
    "I think we should leave now.", "What do you usually do on weekends?",
    "The weather is really nice today.", "I am looking forward to seeing you again."
)

@Composable
private fun ShadowExercise() {
    val speech = rememberSpeech()
    var index by remember { mutableIntStateOf(0) }
    var slow by remember { mutableStateOf(false) }
    var input by remember(index) { mutableStateOf("") }
    var checked by remember(index) { mutableStateOf(false) }
    val target = shadowPhrases[index % shadowPhrases.size]
    val accuracy = ExerciseMetrics.similarity(input, target)
    val distance = ExerciseMetrics.distance(input, target)

    Text("Шаг 1. Прослушайте фразу", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Text(target, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp))
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = { speech.speak(target, if (slow) 0.55f else 0.85f) }) {
            Icon(Icons.Default.VolumeUp, contentDescription = null)
            Text(" Прослушать")
        }
        FilterChip(selected = slow, onClick = { slow = !slow },
            label = { Text(if (slow) "Медленно" else "Обычно") })
    }
    Spacer(Modifier.height(24.dp))
    Text("Шаг 2. Повторите вслух, затем впишите то, что произнесли",
        style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(value = input,
        onValueChange = { input = it; checked = false },
        label = { Text("Произнесённая фраза") },
        maxLines = 3, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Button(onClick = { checked = true }, enabled = input.isNotBlank(),
        modifier = Modifier.fillMaxWidth()) { Text("Проверить") }
    if (checked) {
        Spacer(Modifier.height(16.dp))
        val percentage = (accuracy * 100).toInt()
        Text("Совпадение: " + percentage + "% · Ошибок: " + distance,
            style = MaterialTheme.typography.titleLarge,
            color = if (accuracy >= 0.85) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error)
        LinearProgressIndicator(progress = { accuracy.toFloat() },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Оригинал: " + target)
        Button(onClick = { index++ }, modifier = Modifier.fillMaxWidth()) {
            Text("Следующая фраза")
        }
    }
}

private val dictations = listOf(
    "The early bird catches the worm in the morning",
    "Practice makes perfect when you stay consistent",
    "A journey of a thousand miles begins with a single step",
    "Better late than never but never late is better",
    "Actions speak louder than words in any language",
    "Time flies when you are having fun with friends"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictationExercise() {
    val speech = rememberSpeech()
    var index by remember { mutableIntStateOf(0) }
    var gapEvery by remember { mutableIntStateOf(5) }
    var answers by remember(index, gapEvery) { mutableStateOf(mapOf<Int, String>()) }
    var checked by remember(index, gapEvery) { mutableStateOf(false) }
    val sentence = dictations[index % dictations.size]
    val words = remember(sentence) { sentence.split(Regex("\\s+")) }
    val gaps = remember(index, gapEvery) {
        words.indices.filter { (it + 1) % gapEvery == 0 }.ifEmpty { listOf(words.lastIndex) }
    }
    val correct = gaps.count { indexOfGap ->
        answers[indexOfGap].orEmpty().trim().lowercase()
            .replace(Regex("[^a-z0-9]"), "") ==
        words[indexOfGap].lowercase().replace(Regex("[^a-z0-9]"), "")
    }
    Text("Прослушайте предложение и заполните все пропуски.", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to "Легко", 5 to "Средне", 3 to "Сложно").forEach { (interval, label) ->
            FilterChip(selected = gapEvery == interval,
                onClick = { gapEvery = interval }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(12.dp))
    FilledTonalButton(onClick = { speech.speak(sentence, 0.65f) }) {
        Icon(Icons.Default.Headset, contentDescription = null)
        Text(" Слушать")
    }
    Spacer(Modifier.height(20.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        words.forEachIndexed { i, word ->
            if (i in gaps) {
                OutlinedTextField(
                    value = answers[i].orEmpty(),
                    onValueChange = { value -> if (!checked) answers = answers + (i to value) },
                    enabled = !checked, singleLine = true,
                    placeholder = { Text("...") },
                    modifier = Modifier.width(115.dp),
                    isError = checked &&
                        answers[i].orEmpty().trim().lowercase()
                            .replace(Regex("[^a-z0-9]"), "") !=
                        word.lowercase().replace(Regex("[^a-z0-9]"), "")
                )
            } else Text(word, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp))
        }
    }
    if (checked) {
        Spacer(Modifier.height(12.dp))
        Text("Верно: " + correct + " / " + gaps.size,
            color = if (correct == gaps.size) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error)
        if (correct != gaps.size) Text("Правильный текст: " + sentence)
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = {
        if (checked) index = (index + 1) % dictations.size
        else checked = true
    }) { Text(if (checked) "Далее" else "Проверить") }
}

private val minimalPairs = listOf(
    "ship" to "sheep", "bit" to "beat", "live" to "leave", "fit" to "feet",
    "cat" to "cut", "bag" to "bug", "hat" to "hut", "pen" to "pan",
    "bed" to "bad", "thin" to "sin", "thank" to "sank", "this" to "dis",
    "vest" to "west", "very" to "wary", "rice" to "lice", "right" to "light",
    "walk" to "work", "pull" to "pool", "full" to "fool", "cot" to "caught"
)

@Composable
private fun MinimalPairsExercise() {
    val speech = rememberSpeech()
    var index by remember { mutableIntStateOf(0) }
    var selected by remember(index) { mutableIntStateOf(-1) }
    var streak by remember { mutableIntStateOf(0) }
    val pair = minimalPairs[index % minimalPairs.size]
    val correct = index % 2
    Text("Что вы услышали?", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    FilledTonalButton(onClick = { speech.speak(if (correct == 0) pair.first else pair.second, 0.65f) }) {
        Icon(Icons.Default.VolumeUp, null)
        Text(" Прослушать")
    }
    Spacer(Modifier.height(24.dp))
    listOf(pair.first, pair.second).forEachIndexed { i, word ->
        OutlinedButton(onClick = {
            if (selected < 0) { selected = i; streak = if (i == correct) streak + 1 else 0 }
        }, modifier = Modifier.fillMaxWidth(), enabled = selected < 0) { Text(word) }
    }
    if (selected >= 0) {
        Feedback(selected == correct, if (correct == 0) pair.first else pair.second)
        Button(onClick = { index++ }) { Text("Следующая пара") }
    }
    Text("🔥 Серия: " + streak)
}

private val sentences = listOf(
    "I usually drink coffee in the morning",
    "She has been working here for five years",
    "They are going to the cinema tonight",
    "My brother plays football every weekend",
    "The cat is sleeping on the sofa",
    "We had dinner at a nice restaurant",
    "He cannot speak French very well",
    "Can you tell me where the station is",
    "I would like a cup of tea please",
    "There is a beautiful park near my house",
    "She forgot to bring her umbrella",
    "They have lived in this city since 2020"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuilderExercise() {
    var round by remember { mutableIntStateOf(0) }
    val sentence = sentences[round % sentences.size]
    val words = remember(round) { sentence.split(" ").shuffled() }
    var chosen by remember(round) { mutableStateOf(listOf<Int>()) }
    var checked by remember(round) { mutableStateOf(false) }
    Text("Соберите предложение, нажимая на слова")
    Spacer(Modifier.height(18.dp))
    Text(chosen.joinToString(" ") { words[it] }.ifBlank { "Нажимайте слова ниже" },
        style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        words.forEachIndexed { i, word ->
            FilterChip(selected = i in chosen, onClick = {
                if (i in chosen) chosen = chosen - i
                else chosen = chosen + i
                checked = false
            }, label = { Text(word) })
        }
    }
    Spacer(Modifier.height(16.dp))
    if (checked) Feedback(chosen.joinToString(" ") { words[it] } == sentence, sentence)
    Button(onClick = { if (checked) round++ else checked = true }) {
        Text(if (checked) "Следующее" else "Проверить")
    }
}

private data class Tense(val source: String, val from: String, val to: String, val answers: List<String>)
private val tenses = listOf(
    Tense("I eat breakfast every morning.", "Present Simple", "Past Simple",
        listOf("I ate breakfast every morning.")),
    Tense("She writes a letter to her friend.", "Present Simple", "Present Continuous",
        listOf("She is writing a letter to her friend.")),
    Tense("They play football in the park.", "Present Simple", "Past Continuous",
        listOf("They were playing football in the park.")),
    Tense("He works at a bank.", "Present Simple", "Future Simple",
        listOf("He will work at a bank.", "He'll work at a bank.")),
    Tense("I read a book yesterday.", "Past Simple", "Present Perfect",
        listOf("I have read a book.", "I've read a book.")),
    Tense("We watch a movie tonight.", "Present Simple", "Future Continuous",
        listOf("We will be watching a movie tonight.", "We'll be watching a movie tonight.")),
    Tense("She drinks tea.", "Present Simple", "Past Perfect",
        listOf("She had drunk tea.")),
    Tense("They live in Berlin.", "Present Simple", "Present Perfect",
        listOf("They have lived in Berlin.", "They've lived in Berlin."))
)
private fun normalize(s: String) = s.trim().lowercase().replace(Regex("[.!?]"), "")
    .replace(Regex("\\s+"), " ")

@Composable
private fun TenseExercise() {
    var round by remember { mutableIntStateOf(0) }
    val data = tenses[round % tenses.size]
    var input by remember(round) { mutableStateOf("") }
    var checked by remember(round) { mutableStateOf(false) }
    Text(data.from + " → " + data.to, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(data.source, style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(value = input, onValueChange = { input = it },
        label = { Text("Перепишите предложение") }, modifier = Modifier.fillMaxWidth())
    if (checked) Feedback(data.answers.any { normalize(it) == normalize(input) }, data.answers.first())
    Button(onClick = { if (checked) round++ else checked = true }) {
        Text(if (checked) "Далее" else "Проверить")
    }
}

private val readers = listOf(
    "Morning Routine" to "Every morning I wake up at seven. I open the window and listen to the birds singing in the garden. After a quick shower I make a cup of coffee and read the latest news on my phone. Sometimes I write down a few thoughts in my journal before starting work.",
    "A Walk in the Park" to "On Sunday afternoon we decided to take a long walk in the park. The trees were turning yellow and the air smelled of autumn. Children were running between the benches and a couple of dogs chased a tennis ball across the field. We sat on the grass and shared a thermos of warm tea.",
    "Travel Diary" to "Yesterday I arrived in Lisbon. The streets are narrow and full of colourful tiles. I had grilled fish for lunch near the harbour and watched the boats sailing in the distance. In the evening a street musician was playing fado, and I felt the city slowly opening up to me."
)

@Composable
private fun ReaderExercise(state: CineState) {
    val speech = rememberSpeech()
    var index by remember { mutableIntStateOf(0) }
    var word by remember { mutableStateOf("") }
    val passage = readers[index % readers.size]
    Text(passage.first, style = MaterialTheme.typography.titleLarge)
    Text("Нажмите на слово для перевода, долгий поиск можно выполнить в словаре.")
    Spacer(Modifier.height(16.dp))
    WordFlow(passage.second) { word = it }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = { speech.speak(passage.second) }) { Text("Озвучить текст") }
    Button(onClick = { index++ }) { Text("Следующий текст") }
    if (word.isNotBlank())
        AddWordDialog(state, onDismiss = { word = "" }, defaultWord = word,
            context = passage.second.split(". ").firstOrNull { it.contains(word, true) }.orEmpty())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvisibleExercise() {
    var index by remember { mutableIntStateOf(0) }
    val passage = readers[index % readers.size]
    val words = remember(index) { passage.second.split(" ") }
    val hiddenOrder = remember(index) {
        words.indices.filter { (it + 1) % 4 == 0 && words[it].any(Char::isLetter) }.shuffled()
    }
    var phase by remember(index) { mutableStateOf("preview") }
    var countdown by remember(index) { mutableIntStateOf(5) }
    var hiddenCount by remember(index) { mutableIntStateOf(0) }
    var answers by remember(index) { mutableStateOf(mapOf<Int, String>()) }
    var checked by remember(index) { mutableStateOf(false) }

    LaunchedEffect(index) {
        repeat(5) {
            delay(1000)
            if (phase != "preview") return@LaunchedEffect
            countdown--
        }
        phase = "disappearing"
        while (hiddenCount < hiddenOrder.size) {
            delay(1200)
            if (phase != "disappearing") return@LaunchedEffect
            hiddenCount++
        }
        phase = "recall"
    }

    Text(passage.first, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(when (phase) {
        "preview" -> "Запомните текст: слова начнут исчезать через " + countdown + " сек."
        "disappearing" -> "Слова постепенно исчезают: " + hiddenCount + " / " + hiddenOrder.size
        else -> "Восстановите пропавшие слова по памяти"
    })
    Spacer(Modifier.height(20.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        words.forEachIndexed { i, word ->
            if (i !in hiddenOrder || (phase == "preview") ||
                (phase == "disappearing" && i !in hiddenOrder.take(hiddenCount))) {
                Text(word, style = MaterialTheme.typography.titleMedium)
            } else if (phase == "recall") {
                OutlinedTextField(value = answers[i].orEmpty(),
                    onValueChange = { value -> if (!checked) answers = answers + (i to value) },
                    singleLine = true, enabled = !checked,
                    modifier = Modifier.width((word.length * 11).coerceIn(80, 155).dp),
                    placeholder = { Text("...") },
                    isError = checked && normalizeMissingWord(answers[i].orEmpty()) != normalizeMissingWord(word))
            } else {
                Text("▰▰▰", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    if (phase != "recall") {
        OutlinedButton(onClick = { phase = "recall" }) { Text("Скрыть всё и проверить") }
    } else {
        val correct = hiddenOrder.count {
            normalizeMissingWord(answers[it].orEmpty()) == normalizeMissingWord(words[it])
        }
        if (checked) {
            Feedback(correct == hiddenOrder.size, "Верно " + correct + " / " + hiddenOrder.size)
            if (correct < hiddenOrder.size) Text("Оригинал: " + passage.second)
        }
        Button(onClick = {
            if (checked) index++ else checked = true
        }) { Text(if (checked) "Следующий текст" else "Проверить") }
    }
}

private fun normalizeMissingWord(input: String): String =
    input.lowercase().replace(Regex("[^a-z0-9]"), "")

@Composable
private fun Feedback(correct: Boolean, answer: String) {
    Spacer(Modifier.height(14.dp))
    Text(if (correct) "✓ Верно!" else "Правильный ответ: " + answer,
        color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}
