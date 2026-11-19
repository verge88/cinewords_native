package com.cinewords.nativeapp

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import org.json.JSONObject

/** The bridge is installed ONLY in our own YouTube iframe page, never in third-party movie embeds. */
private class YoutubeTimeBridge(private val status: (Long, Boolean) -> Unit) {
    @JavascriptInterface fun onTime(milliseconds: Long, playing: Boolean) {
        Handler(Looper.getMainLooper()).post { status(milliseconds, playing) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(state: CineState) {
    val video = state.selectedVideo ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Preserve playback accounting when a YouTube search result receives its database UUID.
    val playbackKey = video.youtubeId.ifBlank { video.videoUrl }
    var youtubeIsPlaying by remember(playbackKey) { mutableStateOf(false) }
    val watchCounter = remember(playbackKey) { WatchProgressCounter() }
    val subsEn = remember(playbackKey) { mutableStateListOf<Subtitle>() }
    val subsRu = remember(playbackKey) { mutableStateListOf<Subtitle>() }
    var pos by remember(playbackKey) { mutableLongStateOf(0L) }
    var duration by remember(playbackKey) { mutableLongStateOf(video.durationSec.toLong() * 1000) }
    var speed by remember(playbackKey) { mutableFloatStateOf(1.0f) }
    var showRu by remember(playbackKey) { mutableStateOf(true) }
    var showOnScreen by remember(playbackKey) { mutableStateOf(true) }
    var overlaySubtitles by remember(playbackKey) { mutableStateOf(true) }
    var subtitleOffset by remember(playbackKey) { mutableFloatStateOf(6f) }
    var scale by remember(playbackKey) { mutableFloatStateOf(1.0f) }
    var selectedWord by remember { mutableStateOf("") }
    var shownSettings by remember { mutableStateOf(false) }
    var showTranscript by remember(playbackKey) { mutableStateOf(false) }
    var webView by remember(playbackKey) { mutableStateOf<WebView?>(null) }
    var captionError by remember(playbackKey) { mutableStateOf("") }
    val translatedLines = remember(playbackKey) { mutableStateMapOf<Long, String>() }
    var translating by remember(playbackKey) { mutableStateOf(false) }
    var translationError by remember(playbackKey) { mutableStateOf("") }
    val direct = video.sourceType == "direct" && video.videoUrl.isNotBlank()
    val youtube = video.sourceType == "youtube" && video.youtubeId.isNotBlank()
    val player = remember(playbackKey) {
        if (direct) ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context,
                    DefaultHttpDataSource.Factory().setDefaultRequestProperties(video.httpHeaders))))
            .build().apply {
            setMediaItem(MediaItem.fromUri(video.videoUrl))
            prepare()
            playWhenReady = true
        } else null
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    LaunchedEffect(video) {
        // Persist YouTube search results so subtitles and watched progress
        // share the same database video UUID as in the Flutter version.
        if (youtube && video.id.isBlank()) {
            val saved = runCatching { state.api.saveVideo(video) }.getOrNull()
            if (saved != null && saved.id.isNotBlank()) {
                state.selectedVideo = saved
                return@LaunchedEffect
            }
        }
        try {
            subsEn.clear()
            subsRu.clear()
            subsEn.addAll(state.api.subtitles(video, "en"))
            subsRu.addAll(state.api.subtitles(video, "ru"))
            if (subsEn.isEmpty() && subsRu.isEmpty())
                captionError = "Субтитры не найдены для этого источника"
        } catch (e: Exception) { captionError = e.message.orEmpty() }
    }
    LaunchedEffect(player) {
        while (isActive && player != null) {
            pos = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: duration
            delay(250)
        }
    }
    // Count only seconds while playback is actually running; seeking does not add watch time.
    LaunchedEffect(playbackKey, player) {
        while (isActive) {
            delay(1000)
            val playing = if (direct) player?.isPlaying == true else if (youtube) youtubeIsPlaying else false
            if (watchCounter.tick(playing)) {
                state.reportWatch(state.selectedVideo?.id.orEmpty(), pos, watchCounter.drain())
            }
        }
    }
    DisposableEffect(playbackKey) {
        onDispose {
            val pending = watchCounter.drain()
            if (pending > 0) state.reportWatch(state.selectedVideo?.id.orEmpty(), pos, pending)
        }
    }
    val english = subsEn.lastOrNull { it.startMs <= pos && it.endMs >= pos }
    val russian = subsRu.lastOrNull { it.startMs <= pos && it.endMs >= pos }
    val activeTranslation = if (russian != null) russian.text
        else english?.let { translatedLines[it.startMs] }
    val progress = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.route = state.playerBackRoute }) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                speed = if (speed == 1f) 1.25f else 1f
                player?.playbackParameters = PlaybackParameters(speed)
                webView?.evaluateJavascript("setSpeed(" + speed + ");", null)
            }) { Icon(Icons.Default.Speed, "Скорость") }
            IconButton(onClick = { showRu = !showRu }) {
                Icon(Icons.Default.Translate, "Перевод")
            }
            IconButton(onClick = { showTranscript = !showTranscript }) {
                Icon(if (showTranscript) Icons.Default.Movie else Icons.Default.FormatListBulleted,
                    "Транскрипт")
            }
            IconButton(onClick = { shownSettings = !shownSettings }) {
                Icon(Icons.Default.Settings, "Настройки")
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
            if (direct && player != null) {
                AndroidView(factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.cinewords_player_view, null)
                        as PlayerView).apply { this.player = player }
                }, modifier = Modifier.fillMaxSize())
            } else if (youtube) {
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        addJavascriptInterface(YoutubeTimeBridge { position, playing ->
                            pos = position
                            youtubeIsPlaying = playing
                        }, "CineBridge")
                        loadDataWithBaseURL("https://www.youtube.com", youtubeHtml(video.youtubeId),
                            "text/html", "UTF-8", null)
                        webView = this
                    }
                }, modifier = Modifier.fillMaxSize())
            } else if (video.videoUrl.isNotBlank()) {
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        // Never inject the YouTube JavaScript bridge into external web pages.
                        loadUrl(video.videoUrl)
                        webView = this
                    }
                }, modifier = Modifier.fillMaxSize())
            } else Text("Источник видео не задан")
            if (overlaySubtitles && showOnScreen &&
                (english != null || (showRu && !activeTranslation.isNullOrBlank()))) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, bottom = subtitleOffset.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (english != null) {
                        WordFlow(text = english.text, scale = scale, textColor = Color.White) { word ->
                            selectedWord = word
                        }
                    }
                    if (showRu && !activeTranslation.isNullOrBlank()) {
                        Text(activeTranslation,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * scale))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (direct || youtube) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMs(pos), style = MaterialTheme.typography.labelSmall)
                Slider(value = progress,
                    onValueChange = { fraction ->
                        if (duration > 0) {
                            val seekMs = (duration * fraction).toLong()
                            pos = seekMs
                            if (direct) player?.seekTo(seekMs)
                            else webView?.evaluateJavascript("seekToMs(" + seekMs + ");", null)
                        }
                    }, modifier = Modifier.weight(1f), enabled = duration > 0)
                Text(formatMs(duration), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (shownSettings) {
            ElevatedCard {
                Column(Modifier.padding(14.dp)) {
                    Text("Субтитры и воспроизведение", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Показывать субтитры", Modifier.weight(1f))
                        Switch(checked = showOnScreen, onCheckedChange = { showOnScreen = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Русский перевод", Modifier.weight(1f))
                        Switch(checked = showRu, onCheckedChange = { showRu = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Субтитры поверх видео", Modifier.weight(1f))
                        Switch(checked = overlaySubtitles, onCheckedChange = { overlaySubtitles = it })
                    }
                    if (overlaySubtitles) {
                        Text("Отступ снизу")
                        Slider(value = subtitleOffset, onValueChange = { subtitleOffset = it },
                            valueRange = 0f..50f)
                    }
                    Text("Размер субтитров")
                    Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.7f..1.7f)
                    Text("Скорость: " + speed)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.75f, 1f, 1.25f, 1.5f).forEach { newSpeed ->
                            FilterChip(selected = speed == newSpeed, onClick = {
                                speed = newSpeed
                                player?.playbackParameters = PlaybackParameters(newSpeed)
                                webView?.evaluateJavascript("setSpeed(" + newSpeed + ");", null)
                            }, label = { Text(newSpeed.toString() + "×") })
                        }
                    }
                }
            }
        }
        if (showOnScreen) {
            Spacer(Modifier.height(12.dp))
            Text("Субтитры", style = MaterialTheme.typography.titleMedium)
            if (!overlaySubtitles && english != null) {
                WordFlow(text = english.text, scale = scale) { word -> selectedWord = word }
            }
            val translation = activeTranslation
            if (!overlaySubtitles && showRu && !translation.isNullOrBlank()) {
                Text(translation, style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale),
                    color = MaterialTheme.colorScheme.primary)
            }
            if (showRu && english != null && translation.isNullOrBlank()) {
                TextButton(enabled = !translating, onClick = {
                    translating = true
                    translationError = ""
                    scope.launch {
                        try {
                            translatedLines[english.startMs] = state.api.translateEnglish(english.text)
                        } catch (e: Exception) {
                            translationError = e.message ?: "Не удалось перевести реплику"
                        } finally { translating = false }
                    }
                }) { Text(if (translating) "Перевод…" else "Перевести реплику") }
                if (translationError.isNotBlank())
                    Text(translationError, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
            }
            if (english == null && russian == null && captionError.isNotBlank())
                Text(captionError, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (subsEn.isNotEmpty() && showTranscript) {
            Spacer(Modifier.height(18.dp))
            Text("Транскрипт", style = MaterialTheme.typography.titleLarge)
            subsEn.forEach { line ->
                Row(Modifier.fillMaxWidth().clickable {
                    pos = line.startMs
                    player?.seekTo(pos)
                    if (youtube) webView?.evaluateJavascript("seekToMs(" + pos + ");", null)
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMs(line.startMs), modifier = Modifier.width(58.dp),
                        color = MaterialTheme.colorScheme.primary)
                    Text(line.text)
                }
            }
        }
        if (!showTranscript) {
            Spacer(Modifier.height(22.dp))
            Text(video.title, style = MaterialTheme.typography.headlineSmall)
            if (video.channel.isNotBlank()) Text(video.channel,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(video.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val time = (pos - 5_000L).coerceAtLeast(0L)
                    pos = time
                    player?.seekTo(time)
                    if (youtube) webView?.evaluateJavascript("seekToMs(" + time + ");", null)
                }) { Icon(Icons.Default.Replay5, "На пять секунд назад") }
                IconButton(onClick = {
                    if (direct) {
                        if (player?.isPlaying == true) player.pause() else player?.play()
                    } else if (youtube) {
                        webView?.evaluateJavascript(
                            if (youtubeIsPlaying) "pausePlayback();" else "playPlayback();", null)
                    }
                }) {
                    Icon(if (if (direct) player?.isPlaying == true else youtubeIsPlaying)
                        Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза или воспроизведение")
                }
                IconButton(onClick = {
                    val time = (pos + 5_000L).let { if (duration > 0) it.coerceAtMost(duration) else it }
                    pos = time
                    player?.seekTo(time)
                    if (youtube) webView?.evaluateJavascript("seekToMs(" + time + ");", null)
                }) { Icon(Icons.Default.Forward5, "На пять секунд вперёд") }
                IconButton(enabled = english != null, onClick = {
                    val time = english?.startMs ?: return@IconButton
                    pos = time
                    player?.seekTo(time)
                    if (youtube) webView?.evaluateJavascript("seekToMs(" + time + ");", null)
                }) { Icon(Icons.Default.Replay, "К началу реплики") }
                IconButton(enabled = english != null, onClick = {
                    selectedWord = english?.text.orEmpty()
                }) { Icon(Icons.Default.AddCircleOutline, "Сохранить фразу") }
            }
        }
    }
    if (selectedWord.isNotBlank())
        AddWordDialog(state, onDismiss = { selectedWord = "" }, defaultWord = selectedWord,
            context = english?.text.orEmpty())
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    youtubeIsPlaying = false
                    player?.pause()
                    if (youtube) webView?.evaluateJavascript("pausePlayback();", null)
                    webView?.onPause()
                }
                Lifecycle.Event.ON_START -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(webView) {
        // Capture the owned view; referring to mutable webView in onDispose can destroy
        // a newly created instance when AndroidView assigns it during recomposition.
        val ownedView = webView
        onDispose {
            ownedView?.removeJavascriptInterface("CineBridge")
            ownedView?.stopLoading()
            ownedView?.destroy()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordFlow(text: String, scale: Float = 1.0f, textColor: Color? = null, onWord: (String) -> Unit) {
    val tokens = remember(text) { Regex("""[A-Za-z]+(?:['-][A-Za-z]+)*|[^A-Za-z]+""").findAll(text)
        .map { it.value }.toList() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tokens.forEach { token ->
            if (token.firstOrNull()?.isLetter() == true) {
                Text(token, modifier = Modifier.clickable { onWord(token) },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * scale),
                    color = textColor ?: MaterialTheme.colorScheme.primary)
            } else Text(token, style = MaterialTheme.typography.titleMedium.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize * scale),
                color = textColor ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun formatMs(value: Long): String {
    val s = value.coerceAtLeast(0L) / 1000
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}

private fun youtubeHtml(id: String): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;background:black"><div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player;
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {width:'100%',height:'100%',videoId: ${JSONObject.quote(id)},
    playerVars:{'playsinline':1,'cc_load_policy':0},
    events:{onReady:function(){
      setInterval(function(){try {
        if(player && player.getCurrentTime) CineBridge.onTime(Math.round(player.getCurrentTime()*1000), player.getPlayerState() === YT.PlayerState.PLAYING);
      }catch(e){}},250);
    }}});
}
function seekToMs(ms){if(player && player.seekTo)player.seekTo(ms/1000,true);}
function setSpeed(rate){if(player && player.setPlaybackRate)player.setPlaybackRate(rate);}
function pausePlayback(){if(player && player.pauseVideo)player.pauseVideo();}
function playPlayback(){if(player && player.playVideo)player.playVideo();}
</script></body></html>
""".trimIndent()

class Speech(context: android.content.Context) {
    private var engine: TextToSpeech? = null
    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) engine?.language = Locale.US
        }
    }
    fun speak(text: String, rate: Float = 0.85f) {
        engine?.setSpeechRate(rate)
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cinewords")
    }
    fun stop() { engine?.stop(); engine?.shutdown() }
}

@Composable
fun rememberSpeech(): Speech {
    val context = LocalContext.current
    val speech = remember { Speech(context) }
    DisposableEffect(speech) { onDispose { speech.stop() } }
    return speech
}
