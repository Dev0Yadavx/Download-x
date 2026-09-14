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
            addOption("--no-warnings")
            
            // PO Token warning bypass: iOS client use karein
            addOption("--extractor-args", "youtube:player-client=ios,mweb")
            
            // Android WebView user-agent
            addOption("--user-agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1")
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
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-warnings")
            addOption("--extractor-args", "youtube:player-client=ios,mweb")

            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                addOption("-f", "$formatId+bestaudio/best")
                addOption("--merge-output-format", "mp4")
            }
            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--no-mtime")
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
