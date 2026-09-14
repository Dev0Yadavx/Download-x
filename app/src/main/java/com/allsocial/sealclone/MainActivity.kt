package com.allsocial.sealclone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                SealMainScreen(
                    initialUrl = sharedUrl,
                    onUrlConsumed = { sharedUrl = "" }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            // Pure YouTube links extract karne ka accurate regex
            val urlRegex = Regex("""(https?://[^\s]+)""")
            val matchedUrl = urlRegex.find(text)?.value ?: text
            sharedUrl = matchedUrl.trim()
        }
    }
}

@Composable
fun SealMainScreen(initialUrl: String, onUrlConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val downloader = remember { SealDownloader(context) }

    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var activePlayUrl by remember { mutableStateOf<String?>(null) }

    // Update App Config Dialog states
    val sharedPrefs = remember { context.getSharedPreferences("seal_app_config", Context.MODE_PRIVATE) }
    var autoUpdateEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("auto_update_enabled", false))
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var ytdlpVersion by remember { mutableStateOf("Loading...") }
    var isUpdatingYtdlp by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    // Load initial version & handle auto-update toggle
    LaunchedEffect(Unit) {
        try {
            ytdlpVersion = downloader.getYtDlpVersion()
        } catch (e: Exception) {
            ytdlpVersion = "2024.08.06"
        }

        if (autoUpdateEnabled) {
            scope.launch {
                try {
                    downloader.updateYtDlp()
                    ytdlpVersion = downloader.getYtDlpVersion()
                } catch (e: Exception) {
                    // silent fallback on startup
                }
            }
        }
    }

    LaunchedEffect(showUpdateDialog) {
        if (showUpdateDialog) {
            updateStatusMessage = null
            try {
                ytdlpVersion = downloader.getYtDlpVersion()
            } catch (e: Exception) {
                // keep current
            }
        }
    }

    // Auto-fetch if opened via Share Sheet
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            urlInput = initialUrl
            onUrlConsumed()
            isLoading = true
            videoInfo = null
            scope.launch {
                try {
                    videoInfo = downloader.getMediaInfo(initialUrl)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0C101A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title Bar with Update App Config Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seal",
                    color = Color(0xFF00ADB5),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showUpdateDialog = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update App Config",
                        tint = Color(0xFF00ADB5),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Update App Config",
                        color = Color(0xFFEEEEEE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Input Bar
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste video link...", color = Color.Gray) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00ADB5),
                    unfocusedBorderColor = Color(0xFF28324A)
                ),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            clipboard.getText()?.let {
                                val raw = it.text.toString()
                                val urlRegex = Regex("""(https?://[^\s]+)""")
                                urlInput = urlRegex.find(raw)?.value ?: raw.trim()
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = Color.Gray)
                        }
                        IconButton(
                            onClick = {
                                if (urlInput.isNotBlank() && !isLoading && !isDownloading) {
                                    isLoading = true
                                    videoInfo = null
                                    activePlayUrl = null
                                    scope.launch {
                                        try {
                                            videoInfo = downloader.getMediaInfo(urlInput)
                                        } catch (e: Exception) {
                                            val errorMsg = e.message ?: "Failed to fetch video"
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading && !isDownloading
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = if (!isLoading && !isDownloading) Color(0xFF00ADB5) else Color.Gray)
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF00ADB5))
            }

            if (isDownloading) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00ADB5)
                    )
                    Text(
                        text = "Downloading: ${downloadProgress.toInt()}%",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }
            }

            // Results Card
            videoInfo?.let { info ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        
                        // Thumbnail + Live Play Wrapper
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        ) {
                            if (activePlayUrl != null) {
                                InAppStreamPlayer(url = activePlayUrl!!) {
                                    activePlayUrl = null
                                }
                            } else {
                                AsyncImage(
                                    model = info.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Play Button Trigger
                                val playStream = info.url ?: info.formats?.firstOrNull { it.url != null }?.url
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .align(Alignment.Center)
                                        .clickable {
                                            if (!playStream.isNullOrEmpty()) {
                                                activePlayUrl = playStream
                                            } else {
                                                Toast.makeText(context, "Direct stream loading...", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(info.title ?: "No Title", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(info.uploader ?: "Unknown", color = Color(0xFF00ADB5), fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Audio Button
                        Button(
                            onClick = {
                                if (!isDownloading) {
                                    isDownloading = true
                                    scope.launch {
                                        try {
                                            downloader.startDownload(urlInput, "", true) { p, _ -> downloadProgress = p }
                                            Toast.makeText(context, "Audio saved to Downloads!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            val errorMsg = e.message ?: "Audio error"
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                        } finally {
                                            isDownloading = false
                                        }
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00565B),
                                disabledContainerColor = Color(0xFF00383B)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Audio Only (MP3)")
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Video Qualities", color = Color.Gray, fontSize = 12.sp)

                        // Clean Real Video Formats Only
                        val realFormats = downloader.filterRealVideoFormats(info.formats)

                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(realFormats) { fmt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF22283A))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${fmt.height}p (MP4)", color = Color.White, fontWeight = FontWeight.Medium)

                                    Button(
                                        onClick = {
                                            if (!isDownloading) {
                                                isDownloading = true
                                                scope.launch {
                                                    try {
                                                        downloader.startDownload(urlInput, fmt.formatId ?: "best", false) { p, _ ->
                                                            downloadProgress = p
                                                        }
                                                        Toast.makeText(context, "Video saved to Downloads!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        val errorMsg = e.message ?: "Download failed"
                                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isDownloading = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isDownloading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00ADB5),
                                            disabledContainerColor = Color(0xFF2A4D53)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ytdlp Update Dialog: "Update App Config"
        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isUpdatingYtdlp) showUpdateDialog = false
                },
                containerColor = Color(0xFF161E2E),
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update App Config",
                        tint = Color(0xFF00ADB5),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update App Config",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Show yt-dlp version
                        Surface(
                            color = Color(0xFF0C101A),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "yt-dlp version",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = ytdlpVersion,
                                        color = Color(0xFF00ADB5),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isUpdatingYtdlp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF00ADB5),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        // Auto update on toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C101A))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto update",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically check & update yt-dlp on launch",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { isChecked ->
                                    autoUpdateEnabled = isChecked
                                    sharedPrefs.edit().putBoolean("auto_update_enabled", isChecked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF00ADB5),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFF28324A)
                                )
                            )
                        }

                        // Feedback status message
                        updateStatusMessage?.let { status ->
                            Text(
                                text = status,
                                color = if (status.startsWith("Error")) Color(0xFFFF6B6B) else Color(0xFF4ECCA3),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isUpdatingYtdlp = true
                            updateStatusMessage = "Checking and downloading update..."
                            scope.launch {
                                try {
                                    val result = downloader.updateYtDlp()
                                    val newVer = downloader.getYtDlpVersion()
                                    ytdlpVersion = newVer
                                    updateStatusMessage = "yt-dlp is up to date ($result)"
                                    Toast.makeText(context, "yt-dlp updated successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    val err = e.message ?: "Update failed"
                                    updateStatusMessage = "Error: $err"
                                    Toast.makeText(context, "Update failed: $err", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isUpdatingYtdlp = false
                                }
                            }
                        },
                        enabled = !isUpdatingYtdlp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00ADB5),
                            disabledContainerColor = Color(0xFF2A4D53)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Update now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showUpdateDialog = false },
                        enabled = !isUpdatingYtdlp
                    ) {
                        Text("OK", color = Color(0xFFEEEEEE), fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}

@Composable
fun InAppStreamPlayer(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
        }
    }
}
