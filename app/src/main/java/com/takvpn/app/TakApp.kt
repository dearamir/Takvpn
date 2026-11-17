package com.takvpn.app

import android.app.Application
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
    }
}
