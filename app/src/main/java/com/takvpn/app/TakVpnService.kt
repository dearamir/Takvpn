package com.takvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Base64
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class TakVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var core: CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config != null) Thread { startVpn(config) }.start()
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startVpn(config: String) {
        stopCore()
        try {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "takvpn"
            val key = Base64.encodeToString(
                androidId.toByteArray().copyOf(32),
                Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP
            )
            Libv2ray.initCoreEnv(filesDir.absolutePath, key)

            val pfd = Builder()
                .setSession("Takvpn")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)
                .establish() ?: throw IllegalStateException("ساخت تونل VPN ناموفق بود")
            tun = pfd

            val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(code: Long, msg: String?): Long = 0
            })
            controller.startLoop(config, pfd.fd)
            core = controller
            updateState(true)
        } catch (e: Throwable) {
            stopCore()
            updateState(false)
            post { onError?.invoke(e.message ?: e.toString()) }
        }
    }

    @Synchronized
    private fun stopVpn() {
        stopCore()
        updateState(false)
        stopSelf()
    }

    private fun stopCore() {
        try { core?.stopLoop() } catch (_: Throwable) {}
        core = null
        try { tun?.close() } catch (_: Throwable) {}
        tun = null
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        stopCore()
        updateState(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.takvpn.app.STOP"
        const val EXTRA_CONFIG = "config"

        @Volatile var isRunning = false
        @Volatile var onState: ((Boolean) -> Unit)? = null
        @Volatile var onError: ((String) -> Unit)? = null

        private val main = Handler(Looper.getMainLooper())
        private fun post(r: () -> Unit) { main.post { r() } }
        private fun updateState(v: Boolean) {
            isRunning = v
            post { onState?.invoke(v) }
        }
    }
}
