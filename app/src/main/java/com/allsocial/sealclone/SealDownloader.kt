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

    // 1. Fetch Video Metadata & Formats
    suspend fun getMediaInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        ensureInitialized()
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            // Bot Bypass arguments (Seal default)
            addOption("--extractor-args", "youtube:player-client=android,ios")
            addOption("--socket-timeout", "20")
        }
        YoutubeDL.getInstance().getInfo(request)
    }

    // 2. Download with Progress Hook (Exact Seal Method)
    suspend fun startDownload(
        url: String,
        formatId: String,
        isAudioOnly: Boolean,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        ensureInitialized()
        val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadDir: File = try {
            if (publicDownloadDir != null && (publicDownloadDir.exists() || publicDownloadDir.mkdirs()) && publicDownloadDir.canWrite()) {
                publicDownloadDir
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            }
        } catch (_: Exception) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val request = YoutubeDLRequest(url).apply {
            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                // Video + Audio Automatic FFmpeg Merge
                addOption("-f", "$formatId+bestaudio/best")
                addOption("--merge-output-format", "mp4")
            }
            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--no-mtime")
            addOption("--socket-timeout", "30")
        }

        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            onProgress(progress, line ?: "")
        }
    }

    // 3. Seal In-App Updater for yt-dlp
    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? = withContext(Dispatchers.IO) {
        ensureInitialized()
        YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
    }
}
