package com.takvpn.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

class TakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("takvpn", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            p.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
        ConfigExtras.logPath = File(filesDir, "core.log").absolutePath
        ConfigExtras.hopEnabled = p.getBoolean("hop", true)

        val crashFile = File(filesDir, "crash.txt")
        try {
            if (crashFile.exists()) {
                AppLog.add("کرش قبلی:\n" + crashFile.readText().take(3000))
                crashFile.delete()
            }
        } catch (e: Exception) {
        }
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                crashFile.writeText(Log.getStackTraceString(e))
            } catch (_: Exception) {
            }
            old?.uncaughtException(t, e)
        }
    }
}
