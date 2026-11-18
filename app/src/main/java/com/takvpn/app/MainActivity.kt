package com.takvpn.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.ln
import kotlin.math.sin
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.animation.ValueAnimator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private class Act(val icon: String, val title: String, val sub: String, val run: () -> Unit)

    private data class UiPalette(
        val name: String, val bg: Int, val card: Int, val selected: Int,
        val header: Int, val accent: Int, val text: Int, val soft: Int,
        val bar: Int, val barText: Int, val fab: Int
    )

    private val uiPalettes = listOf(
        UiPalette("آبی اقیانوسی", Color.rgb(12,22,38), Color.rgb(23,39,61), Color.rgb(35,62,91), Color.rgb(8,16,29), Color.rgb(0,205,235), Color.rgb(241,247,255), Color.rgb(157,178,202), Color.rgb(8,16,29), Color.rgb(220,239,250), Color.rgb(10,105,145)),
        UiPalette("مشکی طلایی", Color.rgb(16,16,18), Color.rgb(35,35,40), Color.rgb(61,56,43), Color.rgb(8,8,10), Color.rgb(212,175,55), Color.rgb(248,244,232), Color.rgb(184,180,169), Color.rgb(8,8,10), Color.rgb(245,220,165), Color.rgb(65,55,30)),
        UiPalette("زمردی", Color.rgb(10,25,22), Color.rgb(21,48,41), Color.rgb(32,76,62), Color.rgb(5,17,14), Color.rgb(0,210,145), Color.rgb(239,250,245), Color.rgb(158,193,178), Color.rgb(5,17,14), Color.rgb(218,245,232), Color.rgb(12,105,75)),
        UiPalette("بنفش نئونی", Color.rgb(23,18,39), Color.rgb(43,34,66), Color.rgb(67,52,99), Color.rgb(14,10,27), Color.rgb(170,115,255), Color.rgb(248,244,255), Color.rgb(190,176,216), Color.rgb(14,10,27), Color.rgb(235,224,255), Color.rgb(92,55,150))
    )
    private val textColors = listOf(
        "سفید یخی" to Color.rgb(245,248,255),
        "طلایی" to Color.rgb(245,205,105),
        "فیروزه‌ای" to Color.rgb(0,210,235),
        "سبز زمردی" to Color.rgb(65,220,165),
        "بنفش" to Color.rgb(190,145,255),
        "نقره‌ای" to Color.rgb(190,200,215),
        "نارنجی" to Color.rgb(255,165,70),
        "پیش‌فرض پوسته" to Color.TRANSPARENT
    )
    private fun activePalette() = uiPalettes.find { it.name == prefs.getString("ui_palette", "طلایی") } ?: uiPalettes.first()
    private fun activeTextColor(p: UiPalette = activePalette()): Int =
        textColors.find { it.first == prefs.getString("ui_text_color", "پیش‌فرض پوسته") }?.second
            ?.takeIf { it != Color.TRANSPARENT } ?: p.text

    private fun applyPaletteToTree(view: View, p: UiPalette = activePalette()) {
        when (view) {
            is MaterialCardView -> {
                val bg = when (view.id) {
                    R.id.testBar -> p.bar
                    else -> if (view.strokeWidth > 0) p.selected else p.card
                }
                view.setCardBackgroundColor(bg)
                view.strokeColor = p.accent
            }
            is FloatingActionButton -> {
                view.backgroundTintList = ColorStateList.valueOf(if (TakVpnService.isRunning) p.accent else p.fab)
                view.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            is android.widget.ImageButton -> {
                view.imageTintList = ColorStateList.valueOf(Color.rgb(255,211,106))
                view.backgroundTintList = null
            }
            is TextView -> when (view.id) {
                R.id.titleText, R.id.versionText -> view.setTextColor(p.accent)
                R.id.statusText -> view.setTextColor(if (TakVpnService.isRunning) Color.rgb(90,230,155) else p.soft)
                R.id.pingText -> Unit
                R.id.subText, R.id.usageText, R.id.emptyText, R.id.actSub -> view.setTextColor(p.soft)
                R.id.testText -> view.setTextColor(p.barText)
                else -> {
                    if ((view.parent as? View)?.id == R.id.plusButton) {
                        view.setTextColor(p.accent)
                        view.textSize = 40f
                    } else {
                        view.setTextColor(activeTextColor(p))
                    }
                }
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) applyPaletteToTree(view.getChildAt(i), p)
    }

    private fun applyAppPalette() {
        val p = activePalette()
        findViewById<View>(android.R.id.content)?.let {
            it.setBackgroundColor(p.bg)
            applyPaletteToTree(it, p)
        }
        window.statusBarColor = p.header
        window.navigationBarColor = p.header
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun showAppearanceDialog() {
        val names = uiPalettes.map { it.name }.toTypedArray()
        val original = prefs.getString("ui_palette", null)
        var confirmed = false
        val dialog = AlertDialog.Builder(this)
            .setTitle("انتخاب پوسته")
            .setSingleChoiceItems(names, uiPalettes.indexOf(activePalette())) { _, which ->
                prefs.edit().putString("ui_palette", names[which]).apply()
                applyAppPalette()
            }
            .setPositiveButton("تأیید", null)
            .setNeutralButton("پیش‌فرض", null)
            .setNegativeButton("لغو", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                confirmed = true
                dialog.dismiss()
                toast("پوسته ذخیره شد")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                prefs.edit().remove("ui_palette").apply()
                applyAppPalette()
            }
        }
        dialog.setOnDismissListener {
            if (!confirmed) {
                if (original == null) prefs.edit().remove("ui_palette").apply()
                else prefs.edit().putString("ui_palette", original).apply()
                applyAppPalette()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.04f)
        dialog.window?.decorView?.findViewById<View>(androidx.appcompat.R.id.parentPanel)?.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun showTextColorDialog() {
        val names = textColors.map { it.first }.toTypedArray()
        val original = prefs.getString("ui_text_color", null)
        val selected = names.indexOf(original ?: "پیش‌فرض پوسته").coerceAtLeast(0)
        var confirmed = false
        val dialog = AlertDialog.Builder(this)
            .setTitle("رنگ نوشته‌ها")
            .setSingleChoiceItems(names, selected) { _, which ->
                if (names[which] == "پیش‌فرض پوسته") {
                    prefs.edit().remove("ui_text_color").apply()
                } else {
                    prefs.edit().putString("ui_text_color", names[which]).apply()
                }
                applyAppPalette()
            }
            .setPositiveButton("تأیید", null)
            .setNeutralButton("پیش‌فرض نوشته", null)
            .setNegativeButton("لغو", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                confirmed = true
                dialog.dismiss()
                toast("رنگ نوشته‌ها ذخیره شد")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                prefs.edit().remove("ui_text_color").apply()
                applyAppPalette()
            }
        }
        dialog.setOnDismissListener {
            if (!confirmed) {
                if (original == null) prefs.edit().remove("ui_text_color").apply()
                else prefs.edit().putString("ui_text_color", original).apply()
                applyAppPalette()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.04f)
        dialog.window?.decorView?.findViewById<View>(androidx.appcompat.R.id.parentPanel)?.setBackgroundColor(Color.TRANSPARENT)
    }

    private lateinit var store: ConfigStore
    private lateinit var subs: SubStore
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var testText: TextView
    private lateinit var usageText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var connectIcon: ConnectTDrawable
    private lateinit var titleGlow: GlowAnimator
    private lateinit var statusGlow: GlowAnimator
    private var versionShine: ValueAnimator? = null
    private var connectColorAnimator: ValueAnimator? = null
    private lateinit var flowSpeedView: FlowSpeedView
    private var connectColorAnimator: ValueAnimator? = null
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
            resetConnectVisual()
            toast("اجازه‌ی VPN داده نشد")
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) addFromText(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
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
        flowSpeedView = FlowSpeedView(this)
        findViewById<FrameLayout>(R.id.speedIndicatorSlot).addView(flowSpeedView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        connectIcon = ConnectTDrawable()
        fab.setImageDrawable(connectIcon)
        fab.imageTintList = null
        titleGlow = GlowAnimator(titleText, Color.parseColor("#FFB800"), Color.parseColor("#FFF8D6"))
        statusGlow = GlowAnimator(statusText, c(R.color.status_on), Color.WHITE)

        val versionText = findViewById<TextView>(R.id.versionText)
        versionText.text = verName()
        setupVersionShine(versionText)
        findViewById<TextView>(R.id.coreVersionCorner).text = "Xray " + BuildConfig.XRAY_CORE_VERSION
        findViewById<View>(R.id.plusButton).setOnClickListener { showActions() }
        findViewById<View>(R.id.testBar).setOnClickListener {
            if (TakVpnService.isRunning) runConnectionTest() else toast("اول وصل بشید")
        }
        fab.setOnClickListener { onConnectClicked() }

        render()
        applyAppPalette()

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
        maybeCheckForUpdates()
        versionShine?.takeIf { !it.isRunning }?.start()
    }

    override fun onStop() {
        super.onStop()
        TakVpnService.onState = null
        TakVpnService.onError = null
        TakVpnService.onSwitched = null
        ui.removeCallbacks(ticker)
        titleGlow.stop(c(R.color.header_title))
        statusGlow.stop(c(if (TakVpnService.isRunning) R.color.status_on else R.color.status_off))
        versionShine?.cancel()
        connectIcon.stopShine()
        connectColorAnimator?.cancel()
        connectColorAnimator = null
        flowSpeedView.stopFlow()
        saveTotal()
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
        ui.removeCallbacksAndMessages(null)
    }

    private fun setupVersionShine(view: TextView) {
        view.post {
            if (view.width <= 0 || isFinishing || isDestroyed) return@post

            val textWidth = view.paint.measureText(view.text.toString()).coerceAtLeast(view.width.toFloat())
            val shineWidth = textWidth * 1.4f
            val matrix = Matrix()
            val gradient = LinearGradient(
                0f, 0f, shineWidth, 0f,
                intArrayOf(
                    c(R.color.header_sub),
                    c(R.color.header_sub),
                    Color.WHITE,
                    Color.parseColor("#FFFFD1"),
                    c(R.color.header_sub)
                ),
                floatArrayOf(0f, 0.38f, 0.48f, 0.54f, 1f),
                Shader.TileMode.CLAMP
            )
            view.paint.shader = gradient

            versionShine = ValueAnimator.ofFloat(-shineWidth, textWidth + shineWidth).apply {
                duration = 2200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    matrix.setTranslate(animation.animatedValue as Float, 0f)
                    gradient.setLocalMatrix(matrix)
                    view.invalidate()
                }
                start()
            }
        }
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
                Act("🔗", "افزودن اشتراک", "لینک سابسکریپشن") { addSubDialog() },
                Act("✍️", "ساخت دستی", "فرم خالی کانفیگ") {
                    ConfigEditor.createConfig(this, store) { render() }
                }
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
                Act("🎨", "انتخاب پوسته", "تغییر رنگ پس‌زمینه و کارت‌ها") { showAppearanceDialog() },
                Act("🖋️", "رنگ نوشته‌ها", "تغییر مستقل رنگ متن‌ها") { showTextColorDialog() },
                Act("⬆️", "بررسی نسخه", "نسخه‌ی جدید؟") { checkUpdate() },
                Act("ℹ️", "درباره", "نسخه و دانلود") { showAbout() }
            )
        )

        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
        applyPaletteToTree(root)
        root.setBackgroundColor(activePalette().bg)
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
            .setMessage(
                "نسخه‌ی برنامه: ${verName()}\nنسخه‌ی هسته Xray: ${BuildConfig.XRAY_CORE_VERSION}\nبروزرسانی مستقیم از داخل برنامه"
            )
            .setPositiveButton("بررسی بروزرسانی برنامه") { _, _ -> checkUpdate() }
            .setNeutralButton("بررسی بروزرسانی هسته") { _, _ -> checkCoreUpdate() }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun checkCoreUpdate() {
        toast("در حال بررسی نسخه‌ی هسته...")
        pool.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://api.github.com/repos/2dust/AndroidLibXrayLite/releases/latest")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${connection.responseCode}")
                }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latest = release.optString("tag_name").removePrefix("v")
                val current = BuildConfig.XRAY_CORE_VERSION
                val newer = compareCoreVersions(latest, current) > 0
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (newer) {
                        AlertDialog.Builder(this)
                            .setTitle("هسته‌ی جدید موجود است")
                            .setMessage(
                                "هسته‌ی فعلی: $current\nآخرین هسته: $latest\n\nبرای حفظ امنیت و سازگاری، هسته همراه نسخه‌ی جدید Takvpn نصب می‌شود."
                            )
                            .setPositiveButton("بررسی بروزرسانی برنامه") { _, _ -> checkUpdate() }
                            .setNegativeButton("بعداً", null)
                            .show()
                    } else {
                        toast("هسته‌ی Xray به‌روزه ($current)")
                    }
                }
            } catch (e: Throwable) {
                val message = (e.message ?: e.toString()).replace("\\n", " ").take(80)
                runOnUiThread {
                    if (!isDestroyed) toast("بررسی هسته ناموفق بود: $message")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun compareCoreVersions(first: String, second: String): Int {
        val a = first.split(".").map { it.toIntOrNull() ?: 0 }
        val b = second.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private var pendingInstallFile: java.io.File? = null

    private fun checkUpdate(silent: Boolean = false) {
        if (!silent) toast("در حال بررسی نسخه...")
        pool.execute {
            try {
                val latest = UpdateChecker.latest()
                prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
                val currentCode = BuildConfig.VERSION_CODE
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (latest.versionCode > currentCode) {
                        val notes = latest.notes.trim().take(1800)
                        AlertDialog.Builder(this)
                            .setTitle("نسخه‌ی جدید ${latest.tag} موجود است")
                            .setMessage(
                                "نسخه‌ی فعلی: ${verName()}\nنسخه‌ی جدید: ${latest.tag}" +
                                    (if (notes.isNotBlank()) "\n\nتغییرات:\n$notes" else "")
                            )
                            .setPositiveButton("دانلود و نصب") { _, _ -> downloadAndInstallUpdate(latest) }
                            .setNegativeButton("بعداً", null)
                            .show()
                    } else if (!silent) {
                        toast("نسخه‌ی شما به‌روزه (${verName()})")
                    }
                }
            } catch (e: Throwable) {
                val message = (e.message ?: e.toString()).replace("\\n", " ").take(100)
                AppLog.add("بررسی نسخه ناموفق: $message")
                if (!silent) runOnUiThread {
                    if (!isDestroyed) toast("بررسی نسخه ناموفق: $message")
                }
            }
        }
    }

    private fun downloadAndInstallUpdate(info: UpdateChecker.Result) {
        val progress = android.app.ProgressDialog(this).apply {
            setTitle("دریافت TakVPN ${info.tag}")
            setMessage("در حال دانلود فایل نصب...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = true
            setCancelable(false)
            show()
        }
        pool.execute {
            var connection: HttpURLConnection? = null
            val target = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Takvpn-${info.tag}.apk")
            try {
                connection = URL(info.downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Takvpn")
                if (connection.responseCode !in 200..299) throw java.io.IOException("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                target.parentFile?.mkdirs()
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(target).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var count: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            if (total > 0) runOnUiThread {
                                if (!isDestroyed && progress.isShowing) {
                                    progress.isIndeterminate = false
                                    progress.max = 100
                                    progress.progress = ((downloaded * 100) / total).toInt()
                                }
                            }
                        }
                    }
                }
                if (!target.exists() || target.length() == 0L) throw java.io.IOException("فایل دانلودشده خالی است")
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256.isNotBlank() && !actualHash.equals(info.sha256, true)) {
                    throw java.io.IOException("اعتبارسنجی فایل نصب ناموفق بود")
                }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (progress.isShowing) progress.dismiss()
                    pendingInstallFile = target
                    installDownloadedApk(target)
                }
            } catch (e: Throwable) {
                target.delete()
                val message = (e.message ?: e.toString()).take(120)
                AppLog.add("دانلود بروزرسانی ناموفق: $message")
                runOnUiThread {
                    if (!isDestroyed) {
                        if (progress.isShowing) progress.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("دانلود ناموفق بود")
                            .setMessage(message)
                            .setPositiveButton("باشه", null)
                            .show()
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun installDownloadedApk(file: java.io.File) {
        if (!file.exists()) {
            toast("فایل نصب پیدا نشد؛ دوباره بروزرسانی را بررسی کنید")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = file
            AlertDialog.Builder(this)
                .setTitle("اجازه‌ی نصب برنامه")
                .setMessage("برای نصب نسخه‌ی جدید، اجازه‌ی نصب برنامه از TakVPN را در تنظیمات فعال کنید.")
                .setPositiveButton("رفتن به تنظیمات") { _, _ ->
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("بعداً", null)
                .show()
            return
        }
        try {
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("باز کردن نصب‌کننده ناموفق بود: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile?.let {
                pendingInstallFile = null
                installDownloadedApk(it)
            }
        }
    }

    private fun maybeCheckForUpdates() {
        val last = prefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - last >= 24L * 60L * 60L * 1000L) {
            ui.postDelayed({ if (!isDestroyed) checkUpdate(silent = true) }, 1400L)
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
        flowSpeedView.setRate(((dRx + dTx) / dt).toLong())
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
        flowSpeedView.setRate(0L)
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
        d.setTypeface(null, Typeface.BOLD)
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
            v.findViewById<View>(R.id.editBtn).setOnClickListener {
                ConfigEditor.editConfig(this, store, cfg) { render() }
            }
            v.findViewById<View>(R.id.deleteBtn).setOnClickListener { confirmDelete(cfg) }
            v.findViewById<View>(R.id.shareBtn).setOnClickListener { share(cfg) }
            ping.setOnClickListener { pingOne(cfg) }
            listContainer.addView(v)
        }
        applyAppPalette()
    }

    private fun pingLabel(id: String): Pair<String, Int> {
        val soft = c(R.color.ink_soft)
        return when (val p = pings[id]) {
            null -> "پینگ: برای تست اینجا بزنید" to Color.WHITE
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
        startConnectVisual()
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
        startConnectVisual()
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
        startConnectVisual()
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
        connectIcon.setConnected(running)
        if (running) {
            connectIcon.setFlowing(true)
            if (connectColorAnimator == null) startConnectColorTransition()
        } else resetConnectVisual()
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

    private fun startConnectVisual() {
        connectIcon.setFlowing(true)
        startConnectColorTransition()
    }

    private fun startConnectColorTransition() {
        connectColorAnimator?.cancel()
        connectColorAnimator = ValueAnimator.ofObject(android.animation.ArgbEvaluator(), Color.parseColor("#151515"), Color.parseColor("#FF9800")).apply {
            duration = 10_000L
            interpolator = LinearInterpolator()
            addUpdateListener { fab.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int) }
            start()
        }
    }

    private fun resetConnectVisual() {
        connectColorAnimator?.cancel()
        connectColorAnimator = null
        connectIcon.setFlowing(false)
        fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#151515"))
    }

    private inner class FlowSpeedView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = Color.WHITE }
        private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var level = 0.04f
        private var phase = 0f
        private var waveAnimator: ValueAnimator? = null
        fun setRate(bytesPerSecond: Long) {
            val normalized = (ln(1.0 + bytesPerSecond.coerceAtLeast(0L)) / ln(1.0 + 2_000_000.0)).toFloat().coerceIn(0f, 1f)
            level = 0.04f + normalized * 0.92f
            fill.color = when {
                normalized > 0.66f -> Color.rgb(40, 210, 145)
                normalized > 0.30f -> Color.rgb(255, 175, 55)
                else -> Color.rgb(235, 85, 75)
            }
            invalidate()
        }
        fun stopFlow() { waveAnimator?.cancel(); waveAnimator = null }
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (waveAnimator?.isRunning != true) waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
                duration = 1800L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }; start()
            }
        }
        override fun onDetachedFromWindow() { stopFlow(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = minOf(width, height) * 0.43f
            if (radius <= 0f) return
            val cx = width / 2f; val cy = height / 2f
            val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
            val save = canvas.save(); canvas.clipPath(clip)
            val surfaceY = cy + radius * (0.88f - level * 1.72f)
            val liquid = Path().apply {
                moveTo(cx-radius, cy+radius); lineTo(cx-radius, surfaceY)
                for (step in 0..24) {
                    val px = cx-radius + (2f*radius*step/24f)
                    val py = surfaceY + sin(step/24.0*Math.PI*2.0 + phase.toDouble()).toFloat()*radius*0.10f
                    lineTo(px,py)
                }
                lineTo(cx+radius,cy+radius); close()
            }
            canvas.drawPath(liquid, fill)
            for (i in 0..2) {
                val bx = cx + sin((phase*0.8f+i*2.1f).toDouble()).toFloat()*radius*0.52f
                val by = cy + radius*(0.52f-((phase*0.12f+i*0.31f)%0.72f))
                bubble.color = Color.WHITE; bubble.alpha = 170
                canvas.drawCircle(bx,by,dp(1).toFloat(),bubble)
            }
            canvas.restoreToCount(save); canvas.drawCircle(cx,cy,radius,outline)
        }
    }

    private inner class ConnectTDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        private val matrix = Matrix()
        private val gradient = LinearGradient(
            -72f, 0f, 72f, 0f,
            intArrayOf(
                Color.parseColor("#FFFFA000"),
                Color.parseColor("#FFFFD45C"),
                Color.WHITE,
                Color.WHITE,
                Color.parseColor("#FFFFD45C"),
                Color.parseColor("#FFFFA000")
            ),
            floatArrayOf(0f, 0.32f, 0.44f, 0.56f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        private var shine: ValueAnimator? = null
        private var waveAnimator: ValueAnimator? = null
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f }
        private var flowing = false
        private var wavePhase = 0f
        private var connected = false
        private var offset = -240f

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            if (flowing) {
                val cx = b.exactCenterX(); val cy = b.exactCenterY()
                val radius = minOf(b.width(), b.height()) * 0.47f
                val wave = Path()
                for (i in 0..80) {
                    val angle = i / 80.0 * Math.PI * 2.0
                    val r = radius + sin(angle * 7.0 + wavePhase.toDouble()).toFloat() * 1.4f
                    val px = cx + kotlin.math.cos(angle).toFloat() * r
                    val py = cy + sin(angle).toFloat() * r
                    if (i == 0) wave.moveTo(px, py) else wave.lineTo(px, py)
                }
                wave.close(); ringPaint.color = Color.parseColor("#FFFFA000"); canvas.drawPath(wave, ringPaint)
            }
            paint.textSize = b.height() * 0.82f
            paint.shader = if (connected) gradient else null
            if (connected) {
                matrix.setTranslate(offset, 0f)
                gradient.setLocalMatrix(matrix)
            }
            val y = b.exactCenterY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText("T", b.exactCenterX(), y, paint)
        }

        fun setConnected(value: Boolean) {
            connected = value
            if (value) startShine() else stopShine()
            invalidateSelf()
        }
        fun setFlowing(value: Boolean) {
            flowing = value
            if (value && waveAnimator?.isRunning != true) waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI*2).toFloat()).apply {
                duration = 1600L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { wavePhase = it.animatedValue as Float; invalidateSelf() }; start()
            } else if (!value) { waveAnimator?.cancel(); waveAnimator = null }
            invalidateSelf()
        }

        fun startShine() {
            if (!connected || shine?.isRunning == true) return
            shine = ValueAnimator.ofFloat(-150f, bounds.width().coerceAtLeast(64).toFloat() + 150f).apply {
                duration = 1250L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    offset = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }

        fun stopShine() {
            shine?.cancel(); shine = null
            waveAnimator?.cancel(); waveAnimator = null; flowing = false
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val PING_URL = "https://www.gstatic.com/generate_204"
    }
}