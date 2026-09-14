package com.allsocial.sealclone

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SealDownloader(private val context: Context) {

    private fun ensureInitialized() {
        if (!SealApp.isReady) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                SealApp.isReady = true
            } catch (e: Exception) {
                if (e.message?.contains("already initialized", ignoreCase = true) == true) {
                    SealApp.isReady = true
                }
            }
        }
    }

    // 1. URL sanitization aur Safe Formats Extraction
    suspend fun getMediaInfo(rawInput: String): VideoInfo = withContext(Dispatchers.IO) {
        ensureInitialized()
        val cleanUrl = when {
            rawInput.startsWith("http://") || rawInput.startsWith("https://") -> rawInput.trim()
            rawInput.length in 10..15 && !rawInput.contains(" ") -> "https://www.youtube.com/watch?v=${rawInput.trim()}"
            else -> "ytsearch1:${rawInput.trim()}"
        }

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--ignore-no-formats-error")
            // Web + iOS fallback clients taaki saare standard formats return hon
            addOption("--extractor-args", "youtube:player_client=mweb,ios")
        }
        YoutubeDL.getInstance().getInfo(request)
    }

    // 2. Download Format Fallback (Jo "Requested format is not available" ko rokega)
    suspend fun startDownload(
        rawInput: String,
        formatId: String,
        isAudioOnly: Boolean,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        ensureInitialized()
        val cleanUrl = if (rawInput.startsWith("http")) rawInput.trim() else "https://www.youtube.com/watch?v=${rawInput.trim()}"
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--no-warnings")
            addOption("--extractor-args", "youtube:player_client=mweb,ios")

            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                // Fallback audio selection
                addOption("-f", "ba/b")
            } else {
                // Agar specific formatId fail ho, to auto-fallback best par chale
                addOption("-f", "$formatId+ba/bestvideo+bestaudio/best")
                addOption("--merge-output-format", "mp4")
            }

            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--no-mtime")
        }

        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            onProgress(progress, line ?: "")
        }
    }

    suspend fun getYtDlpVersion(context: Context): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            YoutubeDL.getInstance().version(context.applicationContext) ?: "2024.08.06"
        } catch (e: Exception) {
            "2024.08.06"
        }
    }

    // 3. Seal In-App Updater for yt-dlp
    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? = withContext(Dispatchers.IO) {
        ensureInitialized()
        YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
    }
}
