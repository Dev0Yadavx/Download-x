package com.allsocial.sealclone

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SealApp : Application() {

    companion object {
        @Volatile
        var isReady = false
        var initErrorMessage: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize Native YoutubeDL Core
                YoutubeDL.getInstance().init(applicationContext)
                try {
                    FFmpeg.getInstance().init(applicationContext)
                } catch (e: Exception) {
                    Log.w("SealApp", "FFmpeg init warning: ${e.message}")
                }
                try {
                    Aria2c.getInstance().init(applicationContext)
                } catch (e: Exception) {
                    Log.w("SealApp", "Aria2c init warning: ${e.message}")
                }
                isReady = true
                Log.i("SealApp", "Native engines initialized successfully")
            } catch (e: Exception) {
                initErrorMessage = e.message
                Log.e("SealApp", "Failed to initialize native engines", e)
            }
        }
    }
}
