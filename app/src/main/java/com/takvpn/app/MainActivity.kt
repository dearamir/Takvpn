package com.takvpn.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import libv2ray.Libv2ray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var store: ConfigStore
    private lateinit var subs: SubStore
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var testText: TextView
    private lateinit var usageText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var fab: FloatingActionButton
    private val prefs by lazy { getSharedPreferences("takvpn", Context.MODE_PRIVATE) }
    private val pings = mutableMapOf<String, Long>()
    private val errors = mutableMapOf<String, String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private var pendingConfig: String? = null
    private var lastRunning = false

    private var inited = false
    private var baseRx = 0L
    private var baseTx = 0L
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L
    private var totalBytes = 0L
    private var tick = 0

    private val ticker = object : Runnable {
        override fun run() {
            updateUsage()
            ui.postDelayed(this, 1000)
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConfig?.let { startVpn(it) }
        } else {
            toast("اجازه‌ی VPN داده نشد")
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) addFromText(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = ConfigStore(this)
        subs = SubStore(this, store)
        totalBytes = prefs.getLong("total", 0L)
        statusText = findViewById(R.id.statusText)
        emptyText = findViewById(R.id.emptyText)
        testText = findViewById(R.id.testText)
        usageText = findViewById(R.id.usageText)
        listContainer = findViewById(R.id.listContainer)
        fab = findViewById(R.id.connectFab)

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        findViewById<TextView>(R.id.versionText).text = "نسخه $ver"

        findViewById<MaterialButton>(R.id.pasteButton).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (text.isBlank()) toast("کلیپ‌بورد خالیه") else addFromText(text)
        }
        findViewById<MaterialButton>(R.id.qrButton).setOnClickListener {
            scanLauncher.launch(
                ScanOptions()
                    .setPrompt("QR کانفیگ رو جلوی دوربین بگیرید")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .setCaptureActivity(PortraitCaptureActivity::class.java)
            )
        }
        findViewById<MaterialButton>(R.id.pingAllButton).setOnClickListener { pingAll() }
        findViewById<MaterialButton>(R.id.moreButton).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.testBar).setOnClickListener {
            if (TakVpnService.isRunning) runConnectionTest() else toast("اول وصل بشید")
        }
        fab.setOnClickListener { onConnectClicked() }

        render()
    }

    override fun onStart() {
        super.onStart()
        TakVpnService.onState = { running -> runOnUiThread { updateUi(running) } }
        TakVpnService.onError = { msg ->
            AppLog.add("خطای اتصال: $msg")
            runOnUiThread { toast("خطا: $msg") }
        }
        updateUi(TakVpnService.isRunning)
    }

    override fun onStop() {
        super.onStop()
        TakVpnService.onState = null
        TakVpnService.onError = null
        ui.removeCallbacks(ticker)
        saveTotal()
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
        ui.removeCallbacksAndMessages(null)
    }

    private fun c(id: Int) = ContextCompat.getColor(this, id)

    // ---------- menu ----------

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "افزودن اشتراک (لینک)")
        pm.menu.add(0, 2, 1, "به‌روزرسانی اشتراک‌ها")
        pm.menu.add(0, 3, 2, "مدیریت و حذف اشتراک‌ها")
        pm.menu.add(0, 4, 3, "نمایش لاگ")
        pm.menu.add(0, 5, 4, "حالت تاریک / روشن")
        pm.menu.add(0, 6, 5, "پورت‌هاپینگ Hysteria: " + (if (ConfigExtras.hopEnabled) "روشن" else "خاموش"))
        pm.menu.add(0, 7, 6, "صفر کردن مصرف کل")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> addSubDialog()
                2 -> updateAllSubs()
                3 -> manageSubs()
                4 -> showLog()
                5 -> toggleNight()
                6 -> toggleHop()
                7 -> {
                    totalBytes = 0L
                    saveTotal()
                    if (!TakVpnService.isRunning) usageText.text = "مصرف کل: ${fmt(0)}"
                }
            }
            true
        }
        pm.show()
    }

    private fun toggleNight() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val mode = if (isNight) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putInt("night", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun toggleHop() {
        ConfigExtras.hopEnabled = !ConfigExtras.hopEnabled
        prefs.edit().putBoolean("hop", ConfigExtras.hopEnabled).apply()
        toast("پورت‌هاپینگ " + (if (ConfigExtras.hopEnabled) "روشن شد" else "خاموش شد") + "\nاز اتصال بعدی اعمال می‌شه")
    }

    // ---------- subscriptions ----------

    private fun addSubDialog() {
        val input = EditText(this)
        input.hint = "https://..."
        input.setSingleLine()
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(this)
            .setTitle("لینک اشتراک")
            .setView(box)
            .setPositiveButton("افزودن") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) fetchSub(url)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun hostOf(url: String): String =
        try {
            Uri.parse(url).host ?: "اشتراک"
        } catch (e: Exception) {
            "اشتراک"
        }

    private fun fetchSub(url: String) {
        toast("در حال دریافت اشتراک...")
        AppLog.add("دریافت اشتراک از ${hostOf(url)}")
        pool.execute {
            try {
                val links = SubFetcher.fetch(url)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (links.isEmpty()) {
                        AppLog.add("اشتراک خالی یا بدون کانفیگ پشتیبانی‌شده بود")
                        toast("کانفیگ پشتیبانی‌شده‌ای توی اشتراک پیدا نشد")
                    } else {
                        subs.apply(url, links)
                        render()
                        AppLog.add("${links.size} کانفیگ از ${hostOf(url)} اضافه یا به‌روز شد")
                        toast("${links.size} کانفیگ از اشتراک اضافه یا به‌روز شد")
                    }
                }
            } catch (e: Throwable) {
                val m = (e.message ?: e.toString()).replace("\n", " ").take(80)
                AppLog.add("خطای اشتراک (${hostOf(url)}): $m")
                runOnUiThread { toast("دریافت اشتراک ناموفق: $m") }
            }
        }
    }

    private fun updateAllSubs() {
        val urls = subs.urls()
        if (urls.isEmpty()) {
            toast("اشتراکی ثبت نشده")
            return
        }
        urls.forEach { fetchSub(it) }
    }

    private fun manageSubs() {
        val urls = subs.urls()
        if (urls.isEmpty()) {
            toast("اشتراکی ثبت نشده")
            return
        }
        val labels = urls.map { "${hostOf(it)}  (${subs.count(it)} کانفیگ)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("برای حذف، روی اشتراک بزنید")
            .setItems(labels) { _, i ->
                val url = urls[i]
                AlertDialog.Builder(this)
                    .setTitle("حذف اشتراک")
                    .setMessage("اشتراک ${hostOf(url)} و کانفیگ‌هاش حذف بشه؟")
                    .setPositiveButton("حذف") { _, _ ->
                        if (TakVpnService.isRunning) {
                            toast("اول اتصال رو قطع کنید")
                        } else {
                            subs.remove(url)
                            render()
                        }
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    // ---------- log ----------

    private fun showLog() {
        val core = AppLog.coreLog()
        val text = "— رویدادهای اپ —\n" + AppLog.text().ifEmpty { "(خالی)" } +
            "\n\n— لاگ هسته —\n" + core.ifEmpty { "(خالی)" }
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sv = ScrollView(this)
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("لاگ")
            .setView(sv)
            .setPositiveButton("بستن", null)
            .setNeutralButton("کپی") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log", text))
                toast("لاگ کپی شد")
            }
            .setNegativeButton("پاک کردن") { _, _ ->
                AppLog.clear()
                AppLog.clearCore()
            }
            .show()
    }

    // ---------- usage ----------

    private fun updateUsage() {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx < 0 || tx < 0) {
            usageText.text = "مصرف: روی این گوشی در دسترس نیست"
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!inited) {
            baseRx = rx
            baseTx = tx
            lastRx = rx
            lastTx = tx
            lastTime = now
            inited = true
        }
        val dRx = (rx - lastRx).coerceAtLeast(0L)
        val dTx = (tx - lastTx).coerceAtLeast(0L)
        val dt = ((now - lastTime) / 1000.0).coerceAtLeast(0.5)
        totalBytes += dRx + dTx
        lastRx = rx
        lastTx = tx
        lastTime = now
        tick++
        if (tick % 10 == 0) saveTotal()
        usageText.text =
            "↓ ${fmt(rx - baseRx)} (${fmt((dRx / dt).toLong())}/s)    ↑ ${fmt(tx - baseTx)} (${fmt((dTx / dt).toLong())}/s)\n" +
                "مصرف کل: ${fmt(totalBytes)}"
    }

    private fun startTracking() {
        ui.removeCallbacks(ticker)
        ui.post(ticker)
    }

    private fun stopTracking() {
        ui.removeCallbacks(ticker)
        inited = false
        saveTotal()
        usageText.text = "مصرف کل: ${fmt(totalBytes)}"
    }

    private fun saveTotal() {
        prefs.edit().putLong("total", totalBytes).apply()
    }

    private fun fmt(b: Long): String {
        val v = b.toDouble()
        return when {
            v < 1024 -> "$b B"
            v < 1024.0 * 1024 -> String.format(Locale.US, "%.1f KB", v / 1024)
            v < 1024.0 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", v / 1048576)
            else -> String.format(Locale.US, "%.2f GB", v / 1073741824)
        }
    }

    // ---------- configs ----------

    private fun addFromText(text: String) {
        var added = 0
        var unsupported = 0
        var invalid = 0
        text.split(Regex("(?=\\b(?:vless|vmess|trojan|hysteria2|hysteria|hy2|ss|tuic)://)"))
            .map { it.trim() }
            .filter { it.contains("://") }
            .forEach { item ->
                if (ConfigExtras.isSupported(item)) {
                    try {
                        ConfigParser.buildConfig(item)
                        store.add(item)
                        added++
                    } catch (e: Exception) {
                        invalid++
                    }
                } else {
                    unsupported++
                }
            }
        var msg = if (added > 0) "$added کانفیگ اضافه شد" else "کانفیگ معتبری اضافه نشد"
        if (unsupported > 0) msg += "\n$unsupported مورد پروتکل پشتیبانی‌نشده"
        if (invalid > 0) msg += "\n$invalid مورد نامعتبر"
        AppLog.add(msg.replace("\n", "، "))
        toast(msg)
        if (added > 0) render()
    }

    private fun render() {
        listContainer.removeAllViews()
        emptyText.visibility = if (store.items.isEmpty()) View.VISIBLE else View.GONE
        for (cfg in store.items) {
            val v = layoutInflater.inflate(R.layout.item_config, listContainer, false)
            val card = v as MaterialCardView
            v.findViewById<TextView>(R.id.nameText).text = cfg.name
            val ping = v.findViewById<TextView>(R.id.pingText)
            val (label, color) = pingLabel(cfg.id)
            ping.text = label
            ping.setTextColor(color)
            val selected = cfg.id == store.selectedId
            card.strokeWidth = if (selected) dp(3) else 0
            card.setCardBackgroundColor(c(if (selected) R.color.card_selected else R.color.card))
            card.setOnClickListener {
                store.select(cfg.id)
                render()
            }
            v.findViewById<View>(R.id.editBtn).setOnClickListener { renameDialog(cfg) }
            v.findViewById<View>(R.id.deleteBtn).setOnClickListener { confirmDelete(cfg) }
            v.findViewById<View>(R.id.shareBtn).setOnClickListener { share(cfg) }
            ping.setOnClickListener { pingOne(cfg) }
            listContainer.addView(v)
        }
    }

    private fun pingLabel(id: String): Pair<String, Int> {
        val soft = c(R.color.ink_soft)
        return when (val p = pings[id]) {
            null -> "پینگ: برای تست اینجا بزنید" to soft
            -2L -> "در حال تست..." to soft
            -1L -> {
                val e = errors[id].orEmpty()
                (if (e.isBlank()) "ناموفق" else "ناموفق: $e") to c(R.color.ping_fail)
            }
            else -> "$p ms" to c(if ((p ?: 0L) < 400L) R.color.ping_good else R.color.ping_slow)
        }
    }

    private fun measure(cfg: Cfg): Pair<Long, String> {
        return try {
            CoreEnv.init(applicationContext)
            val j = JSONObject(ConfigParser.buildConfig(cfg.link))
            j.remove("inbounds")
            val ms = Libv2ray.measureOutboundDelay(j.toString(), PING_URL)
            if (ms > 0) ms to "" else -1L to "بدون پاسخ"
        } catch (e: Throwable) {
            -1L to (e.message ?: e.toString()).replace("\n", " ").take(90)
        }
    }

    private fun pingOne(cfg: Cfg) {
        pings[cfg.id] = -2L
        render()
        startPing(cfg)
    }

    private fun pingAll() {
        if (store.items.isEmpty()) {
            toast("کانفیگی وجود نداره")
            return
        }
        store.items.forEach { pings[it.id] = -2L }
        render()
        store.items.toList().forEach { startPing(it) }
    }

    private fun startPing(cfg: Cfg) {
        pool.execute {
            val (ms, err) = measure(cfg)
            if (ms <= 0) AppLog.add("پینگ ناموفق (${cfg.name}): $err")
            runOnUiThread {
                if (!isDestroyed) {
                    errors[cfg.id] = err
                    pings[cfg.id] = ms
                    render()
                }
            }
        }
    }

    private fun runConnectionTest() {
        val cfg = store.selected() ?: return
        testText.text = "در حال تست اتصال..."
        pool.execute {
            val (ms, err) = measure(cfg)
            AppLog.add(if (ms > 0) "تست اتصال: $ms ms" else "تست اتصال ناموفق: $err")
            runOnUiThread {
                if (!isDestroyed) {
                    testText.text = if (ms > 0) {
                        "اتصال در $ms میلی‌ثانیه با موفقیت برقرار شد"
                    } else {
                        "تست ناموفق" + (if (err.isNotEmpty()) ": $err" else "")
                    }
                }
            }
        }
    }

    private fun renameDialog(cfg: Cfg) {
        val input = EditText(this)
        input.setText(cfg.name)
        input.setSelection(input.text.length)
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(this)
            .setTitle("تغییر نام")
            .setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    store.rename(cfg.id, n)
                    render()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun confirmDelete(cfg: Cfg) {
        if (TakVpnService.isRunning && cfg.id == store.selectedId) {
            toast("اول اتصال رو قطع کنید")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("حذف کانفیگ")
            .setMessage("«${cfg.name}» حذف بشه؟")
            .setPositiveButton("حذف") { _, _ ->
                store.remove(cfg.id)
                pings.remove(cfg.id)
                errors.remove(cfg.id)
                render()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun share(cfg: Cfg) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, cfg.link)
        startActivity(Intent.createChooser(send, "اشتراک‌گذاری کانفیگ"))
    }

    // ---------- connect ----------

    private fun onConnectClicked() {
        if (TakVpnService.isRunning) {
            startService(Intent(this, TakVpnService::class.java).setAction(TakVpnService.ACTION_STOP))
            return
        }
        val cfg = store.selected()
        if (cfg == null) {
            toast("اول یه کانفیگ اضافه و انتخاب کنید")
            return
        }
        val json = try {
            ConfigExtras.build(cfg.link)
        } catch (e: Exception) {
            toast("کانفیگ نامعتبر: ${e.message}")
            return
        }
        AppLog.add("درخواست اتصال: ${cfg.name}")
        pendingConfig = json
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpn(json)
    }

    private fun startVpn(config: String) {
        startService(
            Intent(this, TakVpnService::class.java)
                .putExtra(TakVpnService.EXTRA_CONFIG, config)
        )
    }

    private fun updateUi(running: Boolean) {
        fab.setImageResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        fab.backgroundTintList =
            ColorStateList.valueOf(c(if (running) R.color.fab_on else R.color.fab_idle))
        fab.imageTintList =
            ColorStateList.valueOf(if (running) Color.WHITE else c(R.color.fab_icon))
        statusText.text = if (running) "متصل" else "قطع"
        statusText.setTextColor(c(if (running) R.color.status_on else R.color.status_off))
        if (running) {
            if (!lastRunning) {
                AppLog.add("متصل شد: ${store.selected()?.name.orEmpty()}")
                runConnectionTest()
            }
            startTracking()
        } else {
            if (lastRunning) AppLog.add("اتصال قطع شد")
            testText.text = "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید"
            stopTracking()
        }
        lastRunning = running
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val PING_URL = "https://www.gstatic.com/generate_204"
    }
}
