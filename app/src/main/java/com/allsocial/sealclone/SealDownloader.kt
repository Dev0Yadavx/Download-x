package com.allsocial.sealclone

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SealDownloader(private val context: Context) {

    private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (!SealApp.isReady) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                SealApp.isReady = true
            } catch (e: Exception) {
                Log.w("SealDownloader", "Init check: ${e.message}")
            }
        }
    }

    // 1. Clean and repair broken URLs, clip tracking parameters, or resolve Video IDs
    fun cleanAndRepairUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // Full valid link: strip tracking query parameters (?si=, &si=, ?feature=share, etc.)
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
                .replace(Regex("[?&]si=[^&]+"), "")
                .replace(Regex("[?&]feature=share"), "")
                .replace(Regex("[?&]pp=[^&]+"), "")
                .replace("?&", "?")
                .trimEnd('?', '&')
        }

        // Clip cut URL containing pure 11-char video ID
        val idMatch = Regex("([a-zA-Z0-9_-]{11})").find(trimmed)
        return if (idMatch != null && trimmed.length <= 15) {
            "https://www.youtube.com/watch?v=${idMatch.value}"
        } else if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
            "https://$trimmed"
        } else {
            // General query fallback
            "ytsearch1:$trimmed"
        }
    }

    // 2. Metadata Fetch with Single Stable Client to prevent rate limit & audit errors
    suspend fun getMediaInfo(rawUrl: String): VideoInfo = withContext(Dispatchers.IO) {
        ensureInitialized()
        val validUrl = cleanAndRepairUrl(rawUrl)

        val request = YoutubeDLRequest(validUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--ignore-no-formats-error")
            addOption("--no-check-certificates")
            addOption("--no-cache-dir")
            // Use single android client to avoid rapid multi-client 429 rate limits
            addOption("--extractor-args", "youtube:player_client=android")
            addOption("--socket-timeout", "15")
        }

        try {
            YoutubeDL.getInstance().getInfo(request)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("rate limit", ignoreCase = true) || msg.contains("429")) {
                throw Exception("YouTube is rate limiting requests. Please wait a few seconds and try again.")
            }
            throw e
        }
    }

    // 3. Filter Storyboard & Non-Video formats
    fun filterRealVideoFormats(formats: List<VideoFormat>?): List<VideoFormat> {
        if (formats == null) return emptyList()
        return formats
            .filter { fmt ->
                val h = fmt.height ?: 0
                val ext = (fmt.ext ?: "").lowercase()
                val vcodec = fmt.vcodec ?: "none"
                h >= 240 && ext != "mhtml" && ext != "webp" && vcodec != "none"
            }
            .distinctBy { it.height }
            .sortedByDescending { it.height ?: 0 }
    }

    // 4. Safe Download Execution without SELinux audit denials
    suspend fun startDownload(
        rawUrl: String,
        formatId: String,
        isAudioOnly: Boolean,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        ensureInitialized()
        val validUrl = cleanAndRepairUrl(rawUrl)

        // Use scoped app-accessible storage to prevent SELinux permission denials (E/audit)
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val request = YoutubeDLRequest(validUrl).apply {
            addOption("--no-warnings")
            addOption("--no-check-certificates")
            addOption("--no-cache-dir")
            addOption("--extractor-args", "youtube:player_client=android")
            addOption("--socket-timeout", "20")

            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("-f", "ba/b")
            } else {
                val formatQuery = if (formatId.isNotEmpty()) {
                    "$formatId+ba/bestvideo[height<=1080]+bestaudio/best"
                } else {
                    "bv*+ba/b"
                }
                addOption("-f", formatQuery)
                addOption("--merge-output-format", "mp4")
            }

            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--no-mtime")
        }

        try {
            val response = YoutubeDL.getInstance().execute(request) { progress, _, line ->
                onProgress(progress, line ?: "")
            }

            // Trigger MediaScanner so files are visible immediately
            downloadDir.listFiles()?.forEach { file ->
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }
            response
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("rate limit", ignoreCase = true) || msg.contains("429")) {
                throw Exception("Rate limit reached. Please wait a moment before downloading again.")
            }
            throw e
        }
    }

    // 5. Version and Update
    suspend fun getYtDlpVersion(): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            YoutubeDL.getInstance().version(context) ?: "Unknown"
        } catch (e: Throwable) {
            try {
                val request = YoutubeDLRequest(emptyList())
                request.addOption("--version")
                val response = YoutubeDL.getInstance().execute(request)
                response.out?.trim() ?: "Unknown"
            } catch (ex: Throwable) {
                "2024.08.06" // fallback
            }
        }
    }

    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context)
            status?.name ?: "DONE"
        } catch (e: Exception) {
            throw e
        }
    }
}
