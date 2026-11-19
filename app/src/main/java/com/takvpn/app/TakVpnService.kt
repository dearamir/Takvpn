package com.takvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class TakVpnService : VpnService() {

    private val prefs by lazy { getSharedPreferences("takvpn", MODE_PRIVATE) }
    private var tun: ParcelFileDescriptor? = null
    private var core: CoreController? = null
    private var currentConfig: String? = null
    private var cfgName = ""
    private var smartMode = false
    private var foregroundOk = false

    @Volatile
    private var pingText = ""
    private var startedAt = 0L

    @Volatile
    private var wdGen = 0
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
            ACTION_SMART -> {
                smartMode = true
                cfgName = "در حال پیدا کردن بهترین..."
                activeName = ""
                activeLink = ""
                pingText = ""
                showForeground()
                Thread { smartStart() }.start()
                return START_NOT_STICKY
            }
        }
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config != null) {
            val wasRunning = isRunning
            currentConfig = config
            smartMode = intent.getBooleanExtra(EXTRA_SMART, false)
            cfgName = intent.getStringExtra(EXTRA_NAME).orEmpty()
            activeName = cfgName
            activeLink = intent.getStringExtra(EXTRA_LINK).orEmpty()
            pingText = if (wasRunning) "در حال تعویض کانفیگ..." else ""
            showForeground()
            Thread {
                if (wasRunning) switchVpn(config) else startVpn(config)
            }.start()
            startWatchdog()
        }
        return START_NOT_STICKY
    }

    // ---------- notification ----------

    private fun showForeground() {
        foregroundOk = false
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(CHANNEL, "اتصال Takvpn", NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
            startedAt = SystemClock.elapsedRealtime()
            val n = buildNotification()
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, n)
                }
                foregroundOk = true
            } catch (e1: Throwable) {
                try {
                    startForeground(NOTIF_ID, n)
                    foregroundOk = true
                } catch (e2: Throwable) {
                    AppLog.add("اعلان ناموفق: ${e2.javaClass.simpleName} ${e2.message}")
                }
            }
        } catch (e: Throwable) {
            AppLog.add("ساخت اعلان ناموفق: ${e.javaClass.simpleName} ${e.message}")
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
        val label = if (smartMode) "هوشمند: $cfgName" else cfgName
        val text = listOf(label, pingText).filter { it.isNotBlank() }.joinToString("  •  ")
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
        if (!foregroundOk) return
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (e: Throwable) {
        }
    }

    private fun refreshTile() {
        try {
            TileService.requestListeningState(this, ComponentName(this, VpnTileService::class.java))
        } catch (e: Throwable) {
        }
    }

    // ---------- ping / smart switch ----------

    private fun measureMs(config: String): Long =
        try {
            CoreEnv.init(applicationContext)
            val j = JSONObject(config)
            j.remove("inbounds")
            j.remove("routing")
            j.optJSONObject("log")?.remove("error")
            Libv2ray.measureOutboundDelay(j.toString(), PING_URL)
        } catch (e: Throwable) {
            -1L
        }

    private fun doPing() {
        val cfg = currentConfig ?: return
        if (!isRunning) return
        pingText = "پینگ: در حال تست..."
        notifyNow()
        Thread {
            val ms = measureMs(cfg)
            pingText = if (ms > 0) "پینگ: $ms ms" else "پینگ ناموفق"
            handler.post { notifyNow() }
        }.start()
    }

    private fun bestOf(cands: List<Cfg>): Cfg? {
        val ex = Executors.newFixedThreadPool(4)
        try {
            val fs = cands.map { c ->
                ex.submit(Callable<Pair<Cfg, Long>> {
                    val json = try {
                        ConfigExtras.build(c.link)
                    } catch (e: Throwable) {
                        null
                    }
                    c to (if (json != null) measureMs(json) else -1L)
                })
            }
            return fs.mapNotNull {
                try {
                    it.get()
                } catch (e: Exception) {
                    null
                }
            }.filter { it.second > 0 }.minByOrNull { it.second }?.first
        } finally {
            ex.shutdownNow()
        }
    }

    private fun smartStart() {
        val best = bestOf(ConfigStore(this).items.toList())
        if (best == null) {
            AppLog.add("سوییچ هوشمند: کانفیگ سالمی پیدا نشد")
            handler.post {
                shutdownForeground()
                onError?.invoke("کانفیگ سالمی پیدا نشد")
            }
            return
        }
        val json = try {
            ConfigExtras.build(best.link)
        } catch (e: Throwable) {
            handler.post { shutdownForeground() }
            return
        }
        currentConfig = json
        cfgName = best.name
        activeName = best.name
        activeLink = best.link
        AppLog.add("سوییچ هوشمند: ${best.name}")
        startVpn(json)
        startWatchdog()
    }

    private fun startWatchdog() {
        val gen = ++wdGen
        if (!smartMode) return
        Thread {
            var fails = 0
            while (true) {
                try {
                    Thread.sleep(30000)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                if (gen != wdGen || !isRunning) return@Thread
                val cfg = currentConfig ?: return@Thread
                if (measureMs(cfg) > 0) {
                    fails = 0
                } else {
                    fails++
                    AppLog.add("سوییچ هوشمند: تست سلامت ناموفق ($fails/2)")
                    if (fails >= 2) {
                        fails = 0
                        switchBest(gen)
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun switchBest(gen: Int) {
        val best = bestOf(ConfigStore(this).items.toList())
        if (gen != wdGen || !isRunning) return
        if (best == null) {
            AppLog.add("سوییچ هوشمند: کانفیگ سالمی پیدا نشد")
            return
        }
        if (best.link == activeLink) {
            AppLog.add("سوییچ هوشمند: بهترین همین کانفیگ فعلیه")
            return
        }
        val json = try {
            ConfigExtras.build(best.link)
        } catch (e: Throwable) {
            return
        }
        currentConfig = json
        cfgName = best.name
        activeName = best.name
        activeLink = best.link
        pingText = "جابه‌جا شد"
        AppLog.add("سوییچ هوشمند: جابه‌جایی به ${best.name}")
        startVpn(json)
        main.post { onSwitched?.invoke() }
    }

    // ---------- vpn ----------

    private fun finalConfig(config: String): String {
        return try {
            val j = JSONObject(config)
            // Local SOCKS endpoint lets the app query the real public exit IP
            // through this exact Xray outbound, even when the Android VPN excludes
            // the TakVPN app itself to prevent routing loops.
            val inbounds = j.optJSONArray("inbounds") ?: JSONArray()
            val hasProbe = (0 until inbounds.length()).any {
                inbounds.optJSONObject(it)?.optString("tag") == "takvpn-ip-probe"
            }
            if (!hasProbe) {
                inbounds.put(
                    JSONObject()
                        .put("tag", "takvpn-ip-probe")
                        .put("listen", "127.0.0.1")
                        .put("port", IP_PROBE_SOCKS_PORT)
                        .put("protocol", "socks")
                        .put("settings", JSONObject().put("auth", "noauth").put("udp", false))
                )
            }
            j.put("inbounds", inbounds)

            // Force the diagnostic SOCKS inbound through the profile's proxy
            // outbound. Existing routing rules must not send the probe direct.
            val routing = j.optJSONObject("routing") ?: JSONObject()
            val oldRules = routing.optJSONArray("rules") ?: JSONArray()
            val rules = JSONArray().put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("takvpn-ip-probe"))
                    .put("outboundTag", "proxy")
            )
            for (i in 0 until oldRules.length()) {
                val rule = oldRules.optJSONObject(i) ?: continue
                val inboundTags = rule.optJSONArray("inboundTag")
                val isProbeRule = inboundTags != null &&
                    (0 until inboundTags.length()).any { inboundTags.optString(it) == "takvpn-ip-probe" }
                if (!isProbeRule) rules.put(rule)
            }
            routing.put("rules", rules)
            if (!routing.has("domainStrategy")) routing.put("domainStrategy", "AsIs")
            j.put("routing", routing)

            if (prefs.getBoolean("direct_ir", false)) {
                val rule = JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray(listOf("domain:ir")))
                    .put("outboundTag", "direct")
                val routing = j.optJSONObject("routing") ?: JSONObject()
                val old = routing.optJSONArray("rules") ?: JSONArray()
                val rules = JSONArray().put(rule)
                for (i in 0 until old.length()) rules.put(old.get(i))
                routing.put("rules", rules)
                if (!routing.has("domainStrategy")) routing.put("domainStrategy", "AsIs")
                j.put("routing", routing)
            }
            j.toString()
        } catch (e: Throwable) {
            AppLog.add("ساخت مسیر بررسی IP ناموفق: ${e.message}")
            config
        }
    }

    private fun applySplit(b: Builder) {
        val mode = prefs.getInt("split_mode", 0)
        val apps = prefs.getStringSet("split_apps", emptySet()) ?: emptySet()
        var allowed = 0
        if (mode == 1 && apps.isNotEmpty()) {
            apps.forEach {
                try {
                    b.addAllowedApplication(it)
                    allowed++
                } catch (e: Exception) {
                }
            }
        }
        if (allowed == 0) {
            try {
                b.addDisallowedApplication(packageName)
            } catch (e: Exception) {
            }
            if (mode == 2) {
                apps.forEach {
                    try {
                        b.addDisallowedApplication(it)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    // Tear down the previous tunnel and publish a disconnected state before
    // establishing the selected profile. This makes manual profile changes a
    // real VPN replacement instead of only changing the selected card.
    @Synchronized
    private fun switchVpn(config: String) {
        AppLog.add("تعویض کانفیگ: قطع تونل قبلی")
        updateState(false)
        handler.post {
            notifyNow()
            refreshTile()
        }
        stopCore()
        try {
            Thread.sleep(350)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }
        startVpn(config)
    }

    @Synchronized
    private fun startVpn(config: String) {
        stopCore()
        try {
            CoreEnv.init(this)
            val b = Builder()
                .setSession("Takvpn")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
            applySplit(b)
            val dns = parseDns(prefs.getString("dns", DEFAULT_DNS) ?: DEFAULT_DNS)
                .ifEmpty { parseDns(DEFAULT_DNS) }
            dns.forEach {
                try {
                    b.addDnsServer(it)
                } catch (e: Exception) {
                }
            }
            val pfd = b.establish() ?: throw IllegalStateException("ساخت تونل VPN ناموفق بود")
            tun = pfd

            val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(code: Long, msg: String?): Long = 0
            })
            controller.startLoop(finalConfig(config), pfd.fd)
            core = controller
            startedAt = SystemClock.elapsedRealtime()
            updateState(true)
            handler.post {
                notifyNow()
                refreshTile()
            }
        } catch (e: Throwable) {
            stopCore()
            updateState(false)
            val msg = e.message ?: e.toString()
            AppLog.add("اتصال ناموفق: $msg")
            handler.post {
                shutdownForeground()
                refreshTile()
                onError?.invoke(msg)
            }
        }
    }

    @Synchronized
    private fun stopVpn() {
        wdGen++
        stopCore()
        updateState(false)
        shutdownForeground()
        refreshTile()
    }

    private fun shutdownForeground() {
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {
        }
        foregroundOk = false
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
        wdGen++
        stopCore()
        updateState(false)
        refreshTile()
        activeName = ""
        activeLink = ""
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.takvpn.app.STOP"
        const val ACTION_RESTART = "com.takvpn.app.RESTART"
        const val ACTION_PING = "com.takvpn.app.PING"
        const val ACTION_SMART = "com.takvpn.app.SMART"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_SMART = "smart"
        const val EXTRA_NAME = "name"
        const val EXTRA_LINK = "link"
        const val DEFAULT_DNS = "1.1.1.1,8.8.8.8"
        const val IP_PROBE_SOCKS_PORT = 10991
        private const val CHANNEL = "takvpn_conn"
        private const val NOTIF_ID = 1
        private const val PING_URL = "https://www.gstatic.com/generate_204"

        @Volatile
        var isRunning = false

        @Volatile
        var activeName = ""

        @Volatile
        var activeLink = ""

        @Volatile
        var onState: ((Boolean) -> Unit)? = null

        @Volatile
        var onError: ((String) -> Unit)? = null

        @Volatile
        var onSwitched: (() -> Unit)? = null

        private val main = Handler(Looper.getMainLooper())

        private fun updateState(v: Boolean) {
            isRunning = v
            main.post { onState?.invoke(v) }
        }

        fun parseDns(s: String): List<String> {
            val re = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
            return s.split(",", " ", "\n", ";")
                .map { it.trim() }
                .filter { re.matches(it) }
        }
    }
}
