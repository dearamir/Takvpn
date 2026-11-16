package com.takvpn.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import libv2ray.Libv2ray
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var store: ConfigStore
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var testText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var fab: FloatingActionButton
    private val pings = mutableMapOf<String, Long>()
    private val errors = mutableMapOf<String, String>()
    private val pool = Executors.newFixedThreadPool(3)
    private var pendingConfig: String? = null
    private var lastRunning = false

    private val supported = listOf("vless://", "vmess://", "trojan://", "hysteria2://", "hy2://")

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
        statusText = findViewById(R.id.statusText)
        emptyText = findViewById(R.id.emptyText)
        testText = findViewById(R.id.testText)
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
            )
        }
        findViewById<MaterialButton>(R.id.pingAllButton).setOnClickListener { pingAll() }
        findViewById<View>(R.id.testBar).setOnClickListener {
            if (TakVpnService.isRunning) runConnectionTest() else toast("اول وصل بشید")
        }
        fab.setOnClickListener { onConnectClicked() }

        render()
    }

    override fun onStart() {
        super.onStart()
        TakVpnService.onState = { running -> runOnUiThread { updateUi(running) } }
        TakVpnService.onError = { msg -> runOnUiThread { toast("خطا: $msg") } }
        updateUi(TakVpnService.isRunning)
    }

    override fun onStop() {
        super.onStop()
        TakVpnService.onState = null
        TakVpnService.onError = null
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
    }

    private fun addFromText(text: String) {
        var added = 0
        var unsupported = 0
        var invalid = 0
        text.split(Regex("(?=\\b(?:vless|vmess|trojan|hysteria2|hysteria|hy2|ss|tuic)://)"))
            .map { it.trim() }
            .filter { it.contains("://") }
            .forEach { item ->
                if (supported.any { item.startsWith(it) }) {
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
        if (invalid > 0) msg += "\n$invalid مورد نامعتبر (مثلاً obfs)"
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
            card.setCardBackgroundColor(Color.parseColor(if (selected) "#FFF1A6" else "#FFFFFF"))
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
        val soft = Color.parseColor("#5C4A00")
        return when (val p = pings[id]) {
            null -> "پینگ: برای تست اینجا بزنید" to soft
            -2L -> "در حال تست..." to soft
            -1L -> {
                val e = errors[id].orEmpty()
                (if (e.isBlank()) "ناموفق" else "ناموفق: $e") to Color.parseColor("#B00020")
            }
            else -> "$p ms" to Color.parseColor(if ((p ?: 0L) < 400L) "#1B5E20" else "#BF360C")
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
            ConfigParser.buildConfig(cfg.link)
        } catch (e: Exception) {
            toast("کانفیگ نامعتبر: ${e.message}")
            return
        }
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
            ColorStateList.valueOf(Color.parseColor(if (running) "#FF6D00" else "#161100"))
        fab.imageTintList =
            ColorStateList.valueOf(if (running) Color.WHITE else Color.parseColor("#FFD400"))
        statusText.text = if (running) "متصل" else "قطع"
        statusText.setTextColor(Color.parseColor(if (running) "#69F0AE" else "#BBBBBB"))
        if (!running) {
            testText.text = "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید"
        } else if (!lastRunning) {
            runConnectionTest()
        }
        lastRunning = running
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val PING_URL = "https://www.gstatic.com/generate_204"
    }
}
