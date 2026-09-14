package com.allsocial.sealclone

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SealDownloader(private val context: Context) {

    suspend fun getMediaInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url.trim()).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            // mweb aur ios use karne se clear MP4 video streams milti hain
            addOption("--extractor-args", "youtube:player_client=mweb,ios")
        }
        YoutubeDL.getInstance().getInfo(request)
    }

    // Storyboard aur mhtml formats ko filter karne ka function
    fun filterRealVideoFormats(formats: List<VideoFormat>?): List<VideoFormat> {
        if (formats == null) return emptyList()
        return formats
            .filter { fmt ->
                val h = fmt.height ?: 0
                val ext = fmt.ext ?: ""
                // mhtml, webp aur bina video wale junk formats ko block karein
                h >= 240 && ext != "mhtml" && ext != "webp" && fmt.vcodec != "none"
            }
            .distinctBy { it.height }
            .sortedByDescending { it.height }
    }

    suspend fun startDownload(
        url: String,
        formatId: String,
        isAudioOnly: Boolean,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val request = YoutubeDLRequest(url.trim()).apply {
            addOption("--no-warnings")
            addOption("--extractor-args", "youtube:player_client=mweb,ios")

            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("-f", "ba/b")
            } else {
                // Format crash bypass: direct formatId nahi to best fallback
                addOption("-f", "$formatId+ba/bestvideo[height<=1080]+bestaudio/best")
                addOption("--merge-output-format", "mp4")
            }

            addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--no-mtime")
        }

        YoutubeDL.getInstance().execute(request) { progress, _, line ->
            onProgress(progress, line ?: "")
        }
    }
}
