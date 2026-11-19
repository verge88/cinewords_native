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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
                "profile" -> CineProfileScreen(state)
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
    var visiblePassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        Image(painter = painterResource(R.drawable.splash_logo),
            contentDescription = "CineWords", modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(32.dp))
        Text(if (register) "Create Account" else "Welcome back!",
            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Start learning with videos" else "Sign in to continue learning",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        if (register) {
            OutlinedTextField(value = name, onValueChange = { name = it },
                leadingIcon = { Icon(Icons.Default.PersonOutline, null) },
                label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(value = email, onValueChange = { email = it },
            leadingIcon = { Icon(Icons.Default.Email, null,
                tint = MaterialTheme.colorScheme.primary) },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it },
            leadingIcon = { Icon(Icons.Default.LockOutline, null,
                tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                IconButton(onClick = { visiblePassword = !visiblePassword }) {
                    Icon(if (visiblePassword) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff, "Показать пароль")
                }
            },
            label = { Text("Password") },
            visualTransformation = if (visiblePassword) VisualTransformation.None
                else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(32.dp))
        Button(enabled = !busy && email.contains("@") && password.length >= 6 &&
                (!register || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        if (register) {
                            val signedIn = state.api.signUp(email.trim(), password, name.trim())
                            if (!signedIn) message = "Подтвердите email, затем войдите"
                        } else state.api.signIn(email.trim(), password)
                    } catch (e: Exception) { message = e.message.orEmpty() }
                    finally { busy = false }
                }
            }) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp)
            else Text(if (register) "Create Account" else "Sign In")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {
            register = !register; message = ""
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (register) "Sign In" else "Create Account")
        }
        Spacer(Modifier.height(40.dp))
    }
}
