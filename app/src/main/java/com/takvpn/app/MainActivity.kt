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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import libv2ray.Libv2ray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private class Act(val icon: String, val title: String, val sub: String, val run: () -> Unit)

    private lateinit var store: ConfigStore
    private lateinit var subs: SubStore
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var testText: TextView
    private lateinit var usageText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var titleGlow: GlowAnimator
    private lateinit var statusGlow: GlowAnimator
    private val prefs by lazy { getSharedPreferences("takvpn", Context.MODE_PRIVATE) }
    private val pings = mutableMapOf<String, Long>()
    private val errors = mutableMapOf<String, String>()
    private val pool = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())
    private var pendingConfig: String? = null
    private var pendingName = ""
    private var pendingLink = ""
    private var pendingSmart = false
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
        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        emptyText = findViewById(R.id.emptyText)
        testText = findViewById(R.id.testText)
        usageText = findViewById(R.id.usageText)
        listContainer = findViewById(R.id.listContainer)
        fab = findViewById(R.id.connectFab)
        titleGlow = GlowAnimator(titleText, Color.parseColor("#FFB800"), Color.parseColor("#FFF8D6"))
        statusGlow = GlowAnimator(statusText, c(R.color.status_on), Color.WHITE)

        findViewById<TextView>(R.id.versionText).text = "نسخه ${verName()}"
        findViewById<View>(R.id.plusButton).setOnClickListener { showActions() }
        findViewById<View>(R.id.testBar).setOnClickListener {
            if (TakVpnService.isRunning) runConnectionTest() else toast("اول وصل بشید")
        }
        fab.setOnClickListener { onConnectClicked() }

        render()

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
        }

        if (savedInstanceState == null && prefs.getBoolean("auto_open", false) &&
            !TakVpnService.isRunning && store.items.isNotEmpty()
        ) {
            ui.postDelayed({
                if (!isDestroyed && !TakVpnService.isRunning) onConnectClicked()
            }, 700)
        }
    }

    override fun onStart() {
        super.onStart()
        TakVpnService.onState = { running -> runOnUiThread { updateUi(running) } }
        TakVpnService.onError = { msg ->
            AppLog.add("خطای اتصال: $msg")
            runOnUiThread { toast("خطا: $msg") }
        }
        TakVpnService.onSwitched = {
            runOnUiThread {
                render()
                runConnectionTest()
            }
        }
        updateUi(TakVpnService.isRunning)
    }

    override fun onStop() {
        super.onStop()
        TakVpnService.onState = null
        TakVpnService.onError = null
        TakVpnService.onSwitched = null
        ui.removeCallbacks(ticker)
        titleGlow.stop(c(R.color.header_title))
        statusGlow.stop(c(if (TakVpnService.isRunning) R.color.status_on else R.color.status_off))
        saveTotal()
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
        ui.removeCallbacksAndMessages(null)
    }

    private fun c(id: Int) = ContextCompat.getColor(this, id)

    private fun verName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    private fun smartSelected() = prefs.getBoolean("smart_sel", false)

    // ---------- actions sheet ----------

    private fun onOff(key: String) = if (prefs.getBoolean(key, false)) "روشن" else "خاموش"

    private fun showActions() {
        val dialog = BottomSheetDialog(this)
        val root = layoutInflater.inflate(R.layout.sheet_actions, null)
        val container = root.findViewById<LinearLayout>(R.id.actionsContainer)

        addSection(container, "افزودن کانفیگ")
        addGrid(
            container, dialog, R.color.act_add, listOf(
                Act("📋", "کلیپ‌بورد", "چسباندن کانفیگ") { pasteFromClipboard() },
                Act("📷", "اسکن QR", "با دوربین") { launchScan() },
                Act("🔗", "افزودن اشتراک", "لینک سابسکریپشن") { addSubDialog() }
            )
        )
        addSection(container, "ابزارها")
        addGrid(
            container, dialog, R.color.act_tool, listOf(
                Act("⚡", "سریع‌ترین", "تست و اتصال خودکار") { connectFastest() },
                Act("📶", "پینگ همه", "تست همه‌ی کانفیگ‌ها") { pingAll() },
                Act("↕️", "مرتب‌سازی", "بر اساس پینگ") { sortByPing() },
                Act("🧹", "حذف ناموفق‌ها", "کانفیگ‌های بدون پینگ") { deleteDead() }
            )
        )
        addSection(container, "اشتراک‌ها")
        addGrid(
            container, dialog, R.color.act_sub, listOf(
                Act("🔄", "به‌روزرسانی", "همه‌ی اشتراک‌ها") { updateAllSubs() },
                Act("🗂️", "مدیریت", "مشاهده و حذف") { manageSubs() }
            )
        )
        addSection(container, "اتصال و شبکه")
        addGrid(
            container, dialog, R.color.act_sub, listOf(
                Act("🧭", "DNS", prefs.getString("dns", TakVpnService.DEFAULT_DNS).orEmpty()) { dnsDialog() },
                Act("🌐", "سایت‌های .ir مستقیم", onOff("direct_ir")) {
                    toggleSetting("direct_ir", "باز شدن مستقیم سایت‌های .ir")
                },
                Act("🚀", "اتصال هنگام باز شدن", onOff("auto_open")) {
                    toggleSetting("auto_open", "اتصال هنگام باز شدن اپ", "دفعه‌ی بعد که اپ رو باز کنید")
                },
                Act("📲", "انتخاب اپ‌ها", AppPicker.summary(this)) { AppPicker.show(this) }
            )
        )
        addSection(container, "سیستم")
        addGrid(
            container, dialog, R.color.act_sys, listOf(
                Act("🌗", "حالت تاریک", "تاریک / روشن") { toggleNight() },
                Act(
                    "🔀", "پورت‌هاپینگ",
                    "Hysteria: " + (if (ConfigExtras.hopEnabled) "روشن" else "خاموش")
                ) { toggleHop() },
                Act("📜", "لاگ", "رویدادها و خطاها") { showLog() },
                Act("💾", "بکاپ", "کپی همه‌ی کانفیگ‌ها") { backupAll() },
                Act("🧮", "صفر کردن مصرف", "مصرف کل داده") { resetUsage() },
                Act("⬆️", "بررسی نسخه", "نسخه‌ی جدید؟") { checkUpdate() },
                Act("ℹ️", "درباره", "نسخه و دانلود") { showAbout() }
            )
        )

        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private fun addSection(container: LinearLayout, title: String) {
        val tv = TextView(this)
        tv.text = title
        tv.textSize = 14f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setTextColor(c(R.color.ink_soft))
        tv.setPadding(dp(6), dp(14), dp(6), dp(4))
        container.addView(tv)
    }

    private fun addGrid(container: LinearLayout, dialog: BottomSheetDialog, colorRes: Int, acts: List<Act>) {
        var i = 0
        while (i < acts.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val a = acts.getOrNull(i + j)
                if (a == null) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                } else {
                    val v = layoutInflater.inflate(R.layout.item_action, row, false) as MaterialCardView
                    v.setCardBackgroundColor(c(colorRes))
                    v.findViewById<TextView>(R.id.actIcon).text = a.icon
                    v.findViewById<TextView>(R.id.actTitle).text = a.title
                    v.findViewById<TextView>(R.id.actSub).text = a.sub
                    v.setOnClickListener {
                        dialog.dismiss()
                        a.run()
                    }
                    row.addView(v)
                }
            }
            container.addView(row)
            i += 2
        }
    }

    private fun toggleSetting(key: String, title: String, note: String = "از اتصال بعدی اعمال می‌شه") {
        val v = !prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, v).apply()
        toast("$title " + (if (v) "روشن شد" else "خاموش شد") + "\n" + note)
    }

    private fun dnsDialog() {
        val input = EditText(this)
        input.setText(prefs.getString("dns", TakVpnService.DEFAULT_DNS))
        input.hint = "1.1.1.1, 8.8.8.8"
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
            .setTitle("DNS (آدرس‌ها با ویرگول)")
            .setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val list = TakVpnService.parseDns(input.text.toString())
                if (list.isEmpty()) {
                    toast("آدرس IPv4 معتبری وارد نشد")
                } else {
                    prefs.edit().putString("dns", list.joinToString(",")).apply()
                    toast("DNS ذخیره شد. از اتصال بعدی اعمال می‌شه")
                }
            }
            .setNeutralButton("پیش‌فرض") { _, _ ->
                prefs.edit().remove("dns").apply()
                toast("DNS به پیش‌فرض برگشت")
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) toast("کلیپ‌بورد خالیه") else addFromText(text)
    }

    private fun launchScan() {
        scanLauncher.launch(
            ScanOptions()
                .setPrompt("QR کانفیگ رو جلوی دوربین بگیرید")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .setCaptureActivity(PortraitCaptureActivity::class.java)
        )
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

    private fun resetUsage() {
        totalBytes = 0L
        saveTotal()
        if (!TakVpnService.isRunning) usageText.text = "مصرف کل: ${fmt(0)}"
    }

    private fun backupAll() {
        if (store.items.isEmpty()) {
            toast("کانفیگی وجود نداره")
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("configs", store.items.joinToString("\n") { it.link }))
        toast("${store.items.size} کانفیگ توی کلیپ‌بورد کپی شد")
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Takvpn")
            .setMessage("نسخه‌ی ${verName()}\ngithub.com/${UpdateChecker.REPO}")
            .setPositiveButton("بستن", null)
            .setNeutralButton("صفحه‌ی دانلود") { _, _ ->
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${UpdateChecker.REPO}/releases/latest"))
                )
            }
            .show()
    }

    private fun checkUpdate() {
        toast("در حال بررسی نسخه...")
        pool.execute {
            try {
                val r = UpdateChecker.latest()
                val cur = UpdateChecker.lastNumber(verName())
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (r.minor > cur) {
                        AlertDialog.Builder(this)
                            .setTitle("نسخه‌ی جدید ${r.tag}")
                            .setMessage("نسخه‌ی شما ${verName()} است. صفحه‌ی دانلود باز بشه؟")
                            .setPositiveButton("دانلود") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)))
                            }
                            .setNegativeButton("بعداً", null)
                            .show()
                    } else {
                        toast("نسخه‌ی شما به‌روزه (${verName()})")
                    }
                }
            } catch (e: Throwable) {
                val m = (e.message ?: e.toString()).replace("\n", " ").take(80)
                AppLog.add("بررسی نسخه ناموفق: $m")
                runOnUiThread { toast("بررسی نسخه ناموفق: $m") }
            }
        }
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
                val res = SubFetcher.fetch(url)
                val links = res.links
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    res.info?.let { subs.setInfo(url, it) }
                    if (links.isEmpty()) {
                        AppLog.add("اشتراک خالی یا بدون کانفیگ پشتیبانی‌شده بود")
                        toast("کانفیگ پشتیبانی‌شده‌ای توی اشتراک پیدا نشد")
                    } else {
                        subs.apply(url, links)
                        AppLog.add("${links.size} کانفیگ از ${hostOf(url)} اضافه یا به‌روز شد")
                        toast("${links.size} کانفیگ از اشتراک اضافه یا به‌روز شد")
                    }
                    render()
                }
            } catch (e: Throwable) {
                val m = (e.message ?: e.toString()).replace("\n", " ").take(80)
                AppLog.add("خطای اشتراک (${hostOf(url)}): $m")
                runOnUiThread { toast("دریافت اشتراک ناموفق: $m") }
            }
        }
    }

    private fun refreshSubInfo() {
        subs.urls().forEach { url ->
            pool.execute {
                try {
                    val info = SubFetcher.fetch(url).info
                    if (info != null) {
                        runOnUiThread {
                            if (!isDestroyed) {
                                subs.setInfo(url, info)
                                render()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    AppLog.add("به‌روزرسانی اطلاعات اشتراک ناموفق (${hostOf(url)}): ${(e.message ?: "").take(60)}")
                }
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

    private fun subLabel(i: SubInfo): String {
        val parts = mutableListOf<String>()
        if (i.expire > 0) {
            val days = (i.expire * 1000 - System.currentTimeMillis()) / 86400000L
            parts.add(
                when {
                    days < 0 -> "⏳ منقضی شده"
                    days == 0L -> "⏳ کمتر از ۱ روز"
                    else -> "⏳ $days روز"
                }
            )
        } else {
            parts.add("⏳ نامحدود")
        }
        if (i.total > 0) {
            val rem = i.total - i.up - i.down
            parts.add("📦 " + fmt(rem.coerceAtLeast(0L)) + (if (rem <= 0) " (تمام شده)" else ""))
        } else {
            parts.add("📦 نامحدود")
        }
        return parts.joinToString("      ")
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
        val since = prefs.getLong("since", 0L)
        val secs = if (since > 0) (System.currentTimeMillis() - since) / 1000 else 0L
        val dur = String.format(Locale.US, "%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60)
        usageText.text =
            "⏱ $dur    ↓ ${fmt(rx - baseRx)} (${fmt((dRx / dt).toLong())}/s)    ↑ ${fmt(tx - baseTx)} (${fmt((dTx / dt).toLong())}/s)\n" +
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

    private fun addSmartCard() {
        val v = layoutInflater.inflate(R.layout.item_config, listContainer, false)
        val card = v as MaterialCardView
        v.findViewById<TextView>(R.id.nameText).text = "⚡ سوییچ هوشمند"
        val d = v.findViewById<TextView>(R.id.pingText)
        d.text = "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه"
        d.setTextColor(c(R.color.ink_soft))
        v.findViewById<View>(R.id.subText).visibility = View.GONE
        v.findViewById<View>(R.id.editBtn).visibility = View.GONE
        v.findViewById<View>(R.id.deleteBtn).visibility = View.GONE
        v.findViewById<View>(R.id.shareBtn).visibility = View.GONE
        val sel = smartSelected()
        card.strokeWidth = if (sel) dp(3) else 0
        card.setCardBackgroundColor(c(if (sel) R.color.card_selected else R.color.card))
        card.setOnClickListener {
            prefs.edit().putBoolean("smart_sel", true).apply()
            render()
        }
        listContainer.addView(v)
    }

    private fun render() {
        listContainer.removeAllViews()
        emptyText.visibility = if (store.items.isEmpty()) View.VISIBLE else View.GONE
        if (store.items.isNotEmpty()) addSmartCard()
        val smartSel = smartSelected()
        for (cfg in store.items) {
            val v = layoutInflater.inflate(R.layout.item_config, listContainer, false)
            val card = v as MaterialCardView
            v.findViewById<TextView>(R.id.nameText).text = cfg.name
            val ping = v.findViewById<TextView>(R.id.pingText)
            val (label, color) = pingLabel(cfg.id)
            ping.text = label
            ping.setTextColor(color)
            val subView = v.findViewById<TextView>(R.id.subText)
            val info = subs.infoForConfig(cfg.id)
            if (info != null) {
                subView.text = subLabel(info)
                subView.visibility = View.VISIBLE
            } else {
                subView.visibility = View.GONE
            }
            val selected = cfg.id == store.selectedId && !smartSel
            card.strokeWidth = if (selected) dp(3) else 0
            card.setCardBackgroundColor(c(if (selected) R.color.card_selected else R.color.card))
            card.setOnClickListener {
                prefs.edit().putBoolean("smart_sel", false).apply()
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

    private fun measure(link: String): Pair<Long, String> {
        return try {
            CoreEnv.init(applicationContext)
            val j = JSONObject(ConfigParser.buildConfig(link))
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
            val (ms, err) = measure(cfg.link)
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

    private fun testAllThen(done: (List<Cfg>) -> Unit) {
        val list = store.items.toList()
        list.forEach { pings[it.id] = -2L }
        render()
        val left = AtomicInteger(list.size)
        list.forEach { cfg ->
            pool.execute {
                val (ms, err) = measure(cfg.link)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    errors[cfg.id] = err
                    pings[cfg.id] = ms
                    render()
                    if (left.decrementAndGet() == 0) done(list)
                }
            }
        }
    }

    private fun bestOf(list: List<Cfg>): Cfg? =
        list.filter { (pings[it.id] ?: -1L) > 0 }.minByOrNull { pings[it.id] ?: Long.MAX_VALUE }

    private fun connectFastest() {
        if (TakVpnService.isRunning) {
            toast("اول اتصال رو قطع کنید")
            return
        }
        if (store.items.isEmpty()) {
            toast("کانفیگی وجود نداره")
            return
        }
        toast("در حال پیدا کردن سریع‌ترین کانفیگ...")
        testAllThen { list ->
            val best = bestOf(list)
            if (best == null) {
                toast("هیچ کانفیگی پینگ نداد")
            } else {
                prefs.edit().putBoolean("smart_sel", false).apply()
                store.select(best.id)
                render()
                AppLog.add("سریع‌ترین: ${best.name} (${pings[best.id]} ms)")
                connectWith(best, false)
            }
        }
    }

    private fun connectSmart() {
        if (store.items.isEmpty()) {
            toast("کانفیگی وجود نداره")
            return
        }
        testText.text = "در حال پیدا کردن بهترین کانفیگ..."
        fab.isEnabled = false
        testAllThen { list ->
            fab.isEnabled = true
            val best = bestOf(list)
            if (best == null) {
                testText.text = "هیچ کانفیگی پینگ نداد"
                toast("هیچ کانفیگی پینگ نداد")
            } else {
                AppLog.add("سوییچ هوشمند: ${best.name} (${pings[best.id]} ms)")
                connectWith(best, true)
            }
        }
    }

    private fun sortByPing() {
        val sel = store.selectedId
        store.items.sortBy { cfg ->
            when (val p = pings[cfg.id]) {
                null -> Long.MAX_VALUE - 1
                -2L -> Long.MAX_VALUE - 1
                -1L -> Long.MAX_VALUE
                else -> p ?: Long.MAX_VALUE
            }
        }
        store.select(sel)
        render()
        toast("بر اساس پینگ مرتب شد (اول «پینگ همه» رو بزنید)")
    }

    private fun deleteDead() {
        val dead = store.items.filter { pings[it.id] == -1L }
        if (dead.isEmpty()) {
            toast("کانفیگ ناموفقی نیست (اول «پینگ همه» رو بزنید)")
            return
        }
        if (TakVpnService.isRunning) {
            toast("اول اتصال رو قطع کنید")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("حذف ناموفق‌ها")
            .setMessage("${dead.size} کانفیگ ناموفق حذف بشن؟")
            .setPositiveButton("حذف") { _, _ ->
                dead.forEach {
                    store.remove(it.id)
                    pings.remove(it.id)
                    errors.remove(it.id)
                }
                render()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun runConnectionTest() {
        val link = if (TakVpnService.isRunning && TakVpnService.activeLink.isNotEmpty()) {
            TakVpnService.activeLink
        } else {
            store.selected()?.link
        } ?: return
        val smart = smartSelected()
        val name = TakVpnService.activeName
        testText.text = "در حال تست اتصال..."
        pool.execute {
            val (ms, err) = measure(link)
            AppLog.add(if (ms > 0) "تست اتصال: $ms ms" else "تست اتصال ناموفق: $err")
            runOnUiThread {
                if (!isDestroyed) {
                    val prefix = if (smart && name.isNotEmpty()) "هوشمند: $name\n" else ""
                    testText.text = if (ms > 0) {
                        prefix + "اتصال در $ms میلی‌ثانیه با موفقیت برقرار شد"
                    } else {
                        prefix + "تست ناموفق" + (if (err.isNotEmpty()) ": $err" else "")
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
        if (TakVpnService.isRunning && cfg.link == TakVpnService.activeLink) {
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
        AlertDialog.Builder(this)
            .setTitle(cfg.name)
            .setItems(arrayOf("📋 کپی لینک", "📤 ارسال به برنامه‌های دیگه", "🔳 نمایش QR")) { _, i ->
                when (i) {
                    0 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("config", cfg.link))
                        toast("لینک کپی شد")
                    }
                    1 -> {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, cfg.link)
                        startActivity(Intent.createChooser(send, "اشتراک‌گذاری کانفیگ"))
                    }
                    2 -> showQr(cfg)
                }
            }
            .show()
    }

    private fun showQr(cfg: Cfg) {
        val bmp = try {
            BarcodeEncoder().encodeBitmap(cfg.link, BarcodeFormat.QR_CODE, 800, 800)
        } catch (e: Exception) {
            toast("ساخت QR ناموفق بود (لینک خیلی بلنده)")
            return
        }
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setBackgroundColor(Color.WHITE)
        iv.setPadding(dp(12), dp(12), dp(12), dp(12))
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(iv, FrameLayout.LayoutParams(dp(280), dp(280), Gravity.CENTER))
        AlertDialog.Builder(this)
            .setTitle(cfg.name)
            .setView(box)
            .setPositiveButton("بستن", null)
            .show()
    }

    // ---------- connect ----------

    private fun onConnectClicked() {
        if (TakVpnService.isRunning) {
            startService(Intent(this, TakVpnService::class.java).setAction(TakVpnService.ACTION_STOP))
            return
        }
        if (store.items.isEmpty()) {
            toast("اول یه کانفیگ اضافه کنید")
            return
        }
        if (smartSelected()) {
            connectSmart()
            return
        }
        val cfg = store.selected()
        if (cfg == null) {
            toast("اول یه کانفیگ انتخاب کنید")
            return
        }
        connectWith(cfg, false)
    }

    private fun connectWith(cfg: Cfg, smart: Boolean) {
        val json = try {
            ConfigExtras.build(cfg.link)
        } catch (e: Exception) {
            toast("کانفیگ نامعتبر: ${e.message}")
            return
        }
        AppLog.add((if (smart) "اتصال هوشمند: " else "درخواست اتصال: ") + cfg.name)
        pendingConfig = json
        pendingName = cfg.name
        pendingLink = cfg.link
        pendingSmart = smart
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpn(json)
    }

    private fun startVpn(config: String) {
        startService(
            Intent(this, TakVpnService::class.java)
                .putExtra(TakVpnService.EXTRA_CONFIG, config)
                .putExtra(TakVpnService.EXTRA_SMART, pendingSmart)
                .putExtra(TakVpnService.EXTRA_NAME, pendingName)
                .putExtra(TakVpnService.EXTRA_LINK, pendingLink)
        )
    }

    private fun updateUi(running: Boolean) {
        fab.setImageResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        fab.backgroundTintList =
            ColorStateList.valueOf(c(if (running) R.color.fab_on else R.color.fab_idle))
        fab.imageTintList =
            ColorStateList.valueOf(if (running) Color.WHITE else c(R.color.fab_icon))
        statusText.text = if (running) "متصل" else "قطع"
        if (running) {
            if (prefs.getLong("since", 0L) == 0L) {
                prefs.edit().putLong("since", System.currentTimeMillis()).apply()
            }
            if (!lastRunning) {
                AppLog.add("متصل شد: ${TakVpnService.activeName}")
                runConnectionTest()
                refreshSubInfo()
            }
            titleGlow.start()
            statusGlow.start()
            startTracking()
        } else {
            prefs.edit().putLong("since", 0L).apply()
            if (lastRunning) AppLog.add("اتصال قطع شد")
            titleGlow.stop(c(R.color.header_title))
            statusGlow.stop(c(R.color.status_off))
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
