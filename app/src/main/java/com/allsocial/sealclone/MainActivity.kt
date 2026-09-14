package com.allsocial.sealclone

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedUrlState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    onPrimary = Color(0xFF00363D),
                    primaryContainer = Color(0xFF004F58),
                    onPrimaryContainer = Color(0xFF97F0FF),
                    secondary = Color(0xFF80D8FF),
                    background = Color(0xFF0D1B2A),
                    surface = Color(0xFF1B263B),
                    surfaceVariant = Color(0xFF24344D),
                    onBackground = Color(0xFFE0E6ED),
                    onSurface = Color(0xFFE0E6ED),
                    onSurfaceVariant = Color(0xFFB0BEC5)
                )
            ) {
                SealScreen(initialUrl = sharedUrlState ?: "")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                sharedUrlState = text.trim()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealScreen(initialUrl: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val downloader = remember { SealDownloader(context) }

    var urlInput by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var progressStatusText by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var isUpdatingEngine by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var autoUpdateEnabled by remember { mutableStateOf(true) }
    var currentVersion by remember { mutableStateOf("Loading...") }

    LaunchedEffect(showUpdateDialog) {
        if (showUpdateDialog) {
            currentVersion = downloader.getYtDlpVersion(context)
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            urlInput = initialUrl
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Bar / Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Seal Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Seal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Video & Audio Downloader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Update Engine Button
                IconButton(
                    onClick = {
                        showUpdateDialog = true
                    },
                    modifier = Modifier.testTag("update_engine_button")
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = "Update yt-dlp",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Input Card
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                placeholder = { Text("Enter or paste video URL...") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = { urlInput = "" },
                                modifier = Modifier.testTag("clear_url_button")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(
                            onClick = {
                                clipboard.getText()?.let {
                                    urlInput = it.text.trim()
                                }
                            },
                            modifier = Modifier.testTag("paste_url_button")
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                        }
                        IconButton(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    isLoading = true
                                    videoInfo = null
                                    scope.launch {
                                        try {
                                            videoInfo = downloader.getMediaInfo(urlInput.trim())
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("search_video_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing video...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloading...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${downloadProgress.toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        if (progressStatusText.isNotBlank()) {
                            Text(
                                text = progressStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            // Results Card (Title, Thumbnail, Formats)
            videoInfo?.let { info ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!info.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = info.thumbnail,
                                contentDescription = "Video Thumbnail",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Text(
                            text = info.title ?: "Unknown Title",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = info.uploader ?: "Unknown Creator",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Audio Quick Download Button
                        FilledTonalButton(
                            onClick = {
                                isDownloading = true
                                downloadProgress = 0f
                                progressStatusText = "Preparing audio..."
                                scope.launch {
                                    try {
                                        downloader.startDownload(
                                            rawInput = urlInput.trim(),
                                            formatId = "bestaudio",
                                            isAudioOnly = true
                                        ) { p: Float, line: String ->
                                            downloadProgress = p
                                            progressStatusText = line
                                        }
                                        Toast.makeText(context, "Audio saved to Downloads!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isDownloading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_audio_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download Audio Only (MP3)")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Video Qualities",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Format list
                        val validFormats = remember(info) {
                            info.formats?.filter { (it.height ?: 0) > 0 }
                                ?.distinctBy { it.height }
                                ?.sortedByDescending { it.height } ?: emptyList()
                        }

                        if (validFormats.isEmpty()) {
                            // Fallback default format download
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Best Available", fontWeight = FontWeight.Medium)
                                FilledTonalButton(
                                    onClick = {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        progressStatusText = "Starting download..."
                                        scope.launch {
                                            try {
                                                downloader.startDownload(
                                                    rawInput = urlInput.trim(),
                                                    formatId = "best",
                                                    isAudioOnly = false
                                                ) { p: Float, line: String ->
                                                    downloadProgress = p
                                                    progressStatusText = line
                                                }
                                                Toast.makeText(context, "Saved to Downloads!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isDownloading = false
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download")
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                items(validFormats) { fmt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${fmt.height}p (${fmt.ext ?: "mp4"})",
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        FilledTonalButton(
                                            onClick = {
                                                isDownloading = true
                                                downloadProgress = 0f
                                                progressStatusText = "Starting ${fmt.height}p download..."
                                                scope.launch {
                                                    try {
                                                        downloader.startDownload(
                                                            rawInput = urlInput.trim(),
                                                            formatId = fmt.formatId ?: "best",
                                                            isAudioOnly = false
                                                        ) { p: Float, line: String ->
                                                            downloadProgress = p
                                                            progressStatusText = line
                                                        }
                                                        Toast.makeText(context, "Saved to Downloads!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isDownloading = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("download_format_${fmt.height}")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showUpdateDialog) {
            UpdateConfigDialog(
                currentVersion = currentVersion, // e.g. "2026.03.10"
                isAutoUpdateEnabled = autoUpdateEnabled,
                onToggleAutoUpdate = { autoUpdateEnabled = it },
                onUpdateClick = {
                    scope.launch {
                        downloader.updateYtDlp()
                        currentVersion = downloader.getYtDlpVersion(context)
                        Toast.makeText(context, "Config updated!", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}

@Composable
fun UpdateConfigDialog(
    currentVersion: String,
    isAutoUpdateEnabled: Boolean,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E222D), // Modern Dark Slate
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Update Config",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Version Badge Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Engine Version",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = currentVersion,
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SettingsSuggest,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-update Config",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Fetch latest extractor rules automatically to prevent media errors.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isAutoUpdateEnabled,
                        onCheckedChange = onToggleAutoUpdate,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF00E5FF)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onUpdateClick) {
                        Text("Update Now", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = Color.Gray)
                    }
                }
            }
        }
    }
}
