package com.cinewords.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = remember { CineState(applicationContext) }
            CineWordsTheme(state.theme) { CineApp(state) }
        }
    }
}

private val tabs = listOf(
    "home" to "Главная", "catalog" to "Видео", "movies" to "Фильмы",
    "dictionary" to "Слова", "profile" to "Профиль"
)

@Composable
private fun CineApp(state: CineState) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.api.isSignedIn) {
        if (state.api.isSignedIn) {
            state.loadHome()
            state.loadWords()
            state.loadProfile()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error.isNotBlank()) {
            val message = state.error
            state.error = ""
            snackbar.showSnackbar(message)
        }
    }
    LaunchedEffect(state.info) {
        if (state.info.isNotBlank()) {
            val message = state.info
            state.info = ""
            snackbar.showSnackbar(message)
        }
    }
    if (!state.api.isSignedIn) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding)) { AuthScreen(state) }
        }
        return
    }
    val tab = tabs.any { it.first == state.route }
    BackHandler(enabled = !tab || state.route != "home") {
        state.route = if (tab) "home" else when (state.route) {
            "player" -> "video_detail"
            "exercise" -> "exercises"
            "review", "word_detail" -> "dictionary"
            else -> "catalog"
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (tab) NavigationBar(
            modifier = Modifier.height(76.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            tabs.forEachIndexed { index, item ->
                NavigationBarItem(selected = state.route == item.first,
                    onClick = {
                        state.route = item.first
                        when (item.first) {
                            "catalog" -> scope.launch { state.browse("youtube") }
                            "movies" -> scope.launch { state.browse("movies") }
                            "dictionary" -> scope.launch { state.loadWords() }
                            "profile" -> scope.launch { state.loadProfile() }
                        }
                    },
                    icon = { Icon(when (index) {
                        0 -> Icons.Default.Home
                        1 -> Icons.Default.Explore
                        2 -> Icons.Default.Movie
                        3 -> Icons.Default.School
                        else -> Icons.Default.Person
                    }, contentDescription = null) },
                    label = { Text(item.second, maxLines = 1) }
                )
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.route) {
                "home" -> CineHomeScreen(state)
                "catalog", "movies" -> CatalogScreen(state)
                "dictionary" -> DictionaryScreen(state)
                "exercises" -> ExercisesHub(state)
                "profile" -> ProfileScreen(state)
                "video_detail" -> VideoDetailScreen(state)
                "player" -> PlayerScreen(state)
                "review" -> ReviewScreen(state)
                "exercise" -> ExerciseScreen(state)
                "word_detail" -> WordDetailScreen(state)
                else -> CineHomeScreen(state)
            }
            if (state.loading) LinearProgressIndicator(
                Modifier.align(Alignment.TopCenter).fillMaxWidth())
        }
    }
}

@Composable
private fun AuthScreen(state: CineState) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painter = painterResource(R.drawable.splash_logo),
            contentDescription = "CineWords", modifier = Modifier.size(92.dp))
        Spacer(Modifier.height(16.dp))
        Text("CineWords", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Изучайте английский по фильмам и видео",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        if (register) OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("Ваше имя") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = email, onValueChange = { email = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = password, onValueChange = { password = it },
            label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        if (register) {
                            val signedIn = state.api.signUp(email.trim(), password, name)
                            if (!signedIn) message = "Подтвердите email, затем войдите"
                        } else state.api.signIn(email.trim(), password)
                    } catch (e: Exception) { message = e.message.orEmpty() }
                    finally { busy = false }
                }
            }) { Text(if (register) "Зарегистрироваться" else "Войти") }
        TextButton(onClick = { register = !register; message = "" }) {
            Text(if (register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Регистрация")
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}
