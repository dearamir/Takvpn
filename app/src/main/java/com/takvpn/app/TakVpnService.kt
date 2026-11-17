package com.takvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject

class TakVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var core: CoreController? = null
    private var currentConfig: String? = null
    private var cfgName = ""

    @Volatile
    private var pingText = ""
    private var startedAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                val cfg = currentConfig
                if (cfg != null) {
                    pingText = "راه‌اندازی مجدد..."
                    notifyNow()
                    Thread { startVpn(cfg) }.start()
                }
                return START_NOT_STICKY
            }
            ACTION_PING -> {
                doPing()
                return START_NOT_STICKY
            }
        }
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config != null) {
            currentConfig = config
            cfgName = try {
                ConfigStore(this).selected()?.name.orEmpty()
            } catch (e: Exception) {
                ""
            }
            pingText = ""
            showForeground()
            Thread { startVpn(config) }.start()
        }
        return START_NOT_STICKY
    }

    private fun showForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "اتصال Takvpn", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        startedAt = SystemClock.elapsedRealtime()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun act(a: String, req: Int) = PendingIntent.getService(
            this, req,
            Intent(this, TakVpnService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val text = listOf(cfgName, pingText).filter { it.isNotBlank() }.joinToString("  •  ")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_t)
            .setContentTitle("Takvpn متصل است")
            .setContentText(text.ifEmpty { "در حال اتصال..." })
            .setColor(0xFF00C853.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() - elapsed)
            .setContentIntent(open)
            .addAction(0, "توقف", act(ACTION_STOP, 1))
            .addAction(0, "راه‌اندازی مجدد", act(ACTION_RESTART, 2))
            .addAction(0, "پینگ", act(ACTION_PING, 3))
            .build()
    }

    private fun notifyNow() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
        }
    }

    private fun doPing() {
        val cfg = currentConfig ?: return
        if (!isRunning) return
        pingText = "پینگ: در حال تست..."
        notifyNow()
        Thread {
            val ms = try {
                CoreEnv.init(applicationContext)
                val j = JSONObject(cfg)
                j.remove("inbounds")
                j.optJSONObject("log")?.remove("error")
                Libv2ray.measureOutboundDelay(j.toString(), PING_URL)
            } catch (e: Throwable) {
                -1L
            }
            pingText = if (ms > 0) "پینگ: $ms ms" else "پینگ ناموفق"
            handler.post { notifyNow() }
        }.start()
    }

    @Synchronized
    private fun startVpn(config: String) {
        stopCore()
        try {
            CoreEnv.init(this)

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
            startedAt = SystemClock.elapsedRealtime()
            pingText = ""
            updateState(true)
            handler.post { notifyNow() }
        } catch (e: Throwable) {
            stopCore()
            updateState(false)
            val msg = e.message ?: e.toString()
            handler.post {
                shutdownForeground()
                onError?.invoke(msg)
            }
        }
    }

    @Synchronized
    private fun stopVpn() {
        stopCore()
        updateState(false)
        shutdownForeground()
    }

    private fun shutdownForeground() {
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
        }
        stopSelf()
    }

    private fun stopCore() {
        try {
            core?.stopLoop()
        } catch (_: Throwable) {
        }
        core = null
        try {
            tun?.close()
        } catch (_: Throwable) {
        }
        tun = null
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        stopCore()
        updateState(false)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.takvpn.app.STOP"
        const val ACTION_RESTART = "com.takvpn.app.RESTART"
        const val ACTION_PING = "com.takvpn.app.PING"
        const val EXTRA_CONFIG = "config"
        private const val CHANNEL = "takvpn_conn"
        private const val NOTIF_ID = 1
        private const val PING_URL = "https://www.gstatic.com/generate_204"

        @Volatile
        var isRunning = false

        @Volatile
        var onState: ((Boolean) -> Unit)? = null

        @Volatile
        var onError: ((String) -> Unit)? = null

        private val main = Handler(Looper.getMainLooper())

        private fun updateState(v: Boolean) {
            isRunning = v
            main.post { onState?.invoke(v) }
        }
    }
}
