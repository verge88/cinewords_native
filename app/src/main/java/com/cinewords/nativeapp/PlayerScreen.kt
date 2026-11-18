package com.cinewords.nativeapp

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import org.json.JSONObject

/** The bridge is installed ONLY in our own YouTube iframe page, never in third-party movie embeds. */
private class YoutubeTimeBridge(private val time: (Long) -> Unit) {
    @JavascriptInterface fun onTime(milliseconds: Long) {
        Handler(Looper.getMainLooper()).post { time(milliseconds) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(state: CineState) {
    val video = state.selectedVideo ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subsEn = remember(video) { mutableStateListOf<Subtitle>() }
    val subsRu = remember(video) { mutableStateListOf<Subtitle>() }
    var pos by remember(video) { mutableLongStateOf(0L) }
    var duration by remember(video) { mutableLongStateOf(video.durationSec.toLong() * 1000) }
    var speed by remember(video) { mutableFloatStateOf(1.0f) }
    var showRu by remember(video) { mutableStateOf(true) }
    var showOnScreen by remember(video) { mutableStateOf(true) }
    var scale by remember(video) { mutableFloatStateOf(1.0f) }
    var selectedWord by remember { mutableStateOf("") }
    var shownSettings by remember { mutableStateOf(false) }
    var webView by remember(video) { mutableStateOf<WebView?>(null) }
    var captionError by remember(video) { mutableStateOf("") }
    val direct = video.sourceType == "direct" && video.videoUrl.isNotBlank()
    val youtube = video.sourceType == "youtube" && video.youtubeId.isNotBlank()
    val player = remember(video) {
        if (direct) ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.videoUrl))
            prepare()
            playWhenReady = true
        } else null
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    LaunchedEffect(video) {
        try {
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
    val english = subsEn.lastOrNull { it.startMs <= pos && it.endMs >= pos }
    val russian = subsRu.lastOrNull { it.startMs <= pos && it.endMs >= pos }
    val progress = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                scope.launch { state.api.logWatch(video.id, pos, (pos / 1000).toInt()) }
                state.route = "video_detail"
            }) { Text("‹ Назад") }
            Text(video.title, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { shownSettings = !shownSettings }) {
                Icon(Icons.Default.Settings, "Настройки")
            }
        }
        if (direct && player != null) {
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply { this.player = player }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        } else if (youtube) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    addJavascriptInterface(YoutubeTimeBridge { pos = it }, "CineBridge")
                    loadDataWithBaseURL("https://www.youtube.com", youtubeHtml(video.youtubeId),
                        "text/html", "UTF-8", null)
                    webView = this
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        } else if (video.videoUrl.isNotBlank()) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    // No JavascriptInterface on third-party embeds.
                    loadUrl(video.videoUrl)
                    webView = this
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        } else Text("Источник видео не задан")
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
            if (english != null) {
                WordFlow(text = english.text, scale = scale) { word -> selectedWord = word }
            }
            if (showRu && russian != null) {
                Text(russian.text, style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale),
                    color = MaterialTheme.colorScheme.primary)
            }
            if (english == null && russian == null && captionError.isNotBlank())
                Text(captionError, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (subsEn.isNotEmpty()) {
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
    }
    if (selectedWord.isNotBlank())
        AddWordDialog(state, onDismiss = { selectedWord = "" }, defaultWord = selectedWord,
            context = english?.text.orEmpty())
    DisposableEffect(webView) {
        onDispose {
            webView?.removeJavascriptInterface("CineBridge")
            webView?.destroy()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordFlow(text: String, scale: Float = 1.0f, onWord: (String) -> Unit) {
    val tokens = remember(text) { Regex("""[A-Za-z]+(?:['-][A-Za-z]+)*|[^A-Za-z]+""").findAll(text)
        .map { it.value }.toList() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tokens.forEach { token ->
            if (token.firstOrNull()?.isLetter() == true) {
                Text(token, modifier = Modifier.clickable { onWord(token) },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * scale),
                    color = MaterialTheme.colorScheme.primary)
            } else Text(token, style = MaterialTheme.typography.titleMedium.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize * scale))
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
        if(player && player.getCurrentTime) CineBridge.onTime(Math.round(player.getCurrentTime()*1000));
      }catch(e){}},250);
    }}});
}
function seekToMs(ms){if(player && player.seekTo)player.seekTo(ms/1000,true);}
function setSpeed(rate){if(player && player.setPlaybackRate)player.setPlaybackRate(rate);}
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
