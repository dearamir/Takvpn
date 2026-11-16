package com.takvpn.app

import android.content.Context
import android.provider.Settings
import android.util.Base64
import libv2ray.Libv2ray

object CoreEnv {
    @Volatile
    private var done = false

    @Synchronized
    fun init(ctx: Context) {
        if (done) return
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "takvpn"
        val key = Base64.encodeToString(
            androidId.toByteArray().copyOf(32),
            Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP
        )
        Libv2ray.initCoreEnv(ctx.filesDir.absolutePath, key)
        done = true
    }
}
