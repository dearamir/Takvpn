package com.takvpn.app

import android.net.Uri
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

object ConfigEditor {

    private fun tr(act: AppCompatActivity, text: String): String = when (AppLanguage.code(act)) {
        "en" -> mapOf("کانفیگ جدید" to "New config", "ویرایش کانفیگ" to "Edit config", "این کانفیگ قابل ویرایش نیست" to "This config cannot be edited", "ذخیره شد. از اتصال بعدی اعمال می‌شه" to "Saved. Applies on next connection", "نشانی سرور خالیه" to "Server address is empty", "پورت نامعتبره" to "Invalid port", "پورت باید بین ۱ تا ۶۵۵۳۵ باشه" to "Port must be between 1 and 65535", "شناسه (UUID) خالیه" to "UUID is empty", "رمز عبور خالیه" to "Password is empty", "ذخیره" to "Save", "لغو" to "Cancel", "افزودن کانفیگ" to "Add config", "ویرایش کانفیگ" to "Edit config").getOrDefault(text, text)
        "fr" -> mapOf("کانفیگ جدید" to "Nouvelle config", "این کانفیگ قابل ویرایش نیست" to "Cette config ne peut pas être modifiée", "ذخیره" to "Enregistrer", "لغو" to "Annuler", "افزودن کانفیگ" to "Ajouter une config").getOrDefault(text, text)
        "ru" -> mapOf("کانفیگ جدید" to "Новая конфигурация", "این کانفیگ قابل ویرایش نیست" to "Эту конфигурацию нельзя редактировать", "ذخیره" to "Сохранить", "لغو" to "Отмена", "افزودن کانفیگ" to "Добавить конфигурацию").getOrDefault(text, text)
        "zh" -> mapOf("کانفیگ جدید" to "新配置", "این کانفیگ قابل ویرایش نیست" to "无法编辑此配置", "ذخیره" to "保存", "لغو" to "取消", "افزودن کانفیگ" to "添加配置").getOrDefault(text, text)
        "tr" -> mapOf("کانفیگ جدید" to "Yeni yapılandırma", "این کانفیگ قابل ویرایش نیست" to "Bu yapılandırma düzenlenemez", "ذخیره" to "Kaydet", "لغو" to "İptal", "افزودن کانفیگ" to "Yapılandırma ekle").getOrDefault(text, text)
        "de" -> mapOf("کانفیگ جدید" to "Neue Konfiguration", "این کانفیگ قابل ویرایش نیست" to "Diese Konfiguration kann nicht bearbeitet werden", "ذخیره" to "Speichern", "لغو" to "Abbrechen", "افزودن کانفیگ" to "Konfiguration hinzufügen").getOrDefault(text, text)
        "ar" -> mapOf("کانفیگ جدید" to "إعداد جديد", "این کانفیگ قابل ویرایش نیست" to "لا يمكن تعديل هذا الإعداد", "ذخیره" to "حفظ", "لغو" to "إلغاء", "افزودن کانفیگ" to "إضافة إعداد").getOrDefault(text, text)
        "es" -> mapOf("کانفیگ جدید" to "Nueva configuración", "این کانفیگ قابل ویرایش نیست" to "No se puede editar esta configuración", "ذخیره" to "Guardar", "لغو" to "Cancelar", "افزودن کانفیگ" to "Añadir configuración").getOrDefault(text, text)
        "it" -> mapOf("کانفیگ جدید" to "Nuova configurazione", "این کانفیگ قابل ویرایش نیست" to "Impossibile modificare questa configurazione", "ذخیره" to "Salva", "لغو" to "Annulla", "افزودن کانفیگ" to "Aggiungi configurazione").getOrDefault(text, text)
        else -> text
    }

    private class F(
        val key: String,
        val label: String,
        val opts: List<Pair<String, String>>? = null,
        val multi: Boolean = false,
        val numeric: Boolean = false,
        val show: (Map<String, String>) -> Boolean = { true }
    )

    private class Draft(val protocol: String, val params: LinkedHashMap<String, String>)

    private fun t(m: Map<String, String>) = m["type"].orEmpty().ifEmpty { "tcp" }
    private fun sec(m: Map<String, String>) = m["security"].orEmpty().ifEmpty { "none" }
    private fun hostShown(m: Map<String, String>) =
        t(m) in listOf("ws", "httpupgrade", "xhttp") || (t(m) == "tcp" && m["headerType"] == "http")

    private fun same(vararg v: String) = v.map { it to it }

    private fun transport(vmess: Boolean): List<F> = listOf(
        F("type", "شبکه (Network)", same("tcp", "ws", "grpc", "httpupgrade", "xhttp")),
        F("headerType", "نوع هدر", same("none", "http")) { t(it) == "tcp" },
        F("host", "هاست (Host)") { hostShown(it) },
        F("path", "مسیر (Path)") { hostShown(it) },
        F("serviceName", "نام سرویس gRPC") { t(it) == "grpc" },
        F("mode", "حالت xhttp", same("auto", "packet-up", "stream-up", "stream-one")) { t(it) == "xhttp" },
        F("extra", "extra (متن JSON)", multi = true) { t(it) == "xhttp" },
        F(
            "security", "امنیت (TLS)",
            if (vmess) same("none", "tls") else same("none", "tls", "reality")
        ),
        F("sni", "SNI") { sec(it) != "none" },
        F(
            "fp", "اثرانگشت (fp)",
            same("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized")
        ) { sec(it) != "none" },
        F("alpn", "ALPN (مثلاً h2,http/1.1)") { sec(it) == "tls" },
        F("allowInsecure", "اجازه‌ی گواهی ناامن", listOf("0" to "خیر", "1" to "بله")) { sec(it) == "tls" },
        F("pbk", "کلید عمومی (pbk)") { sec(it) == "reality" },
        F("sid", "شناسه کوتاه (sid)") { sec(it) == "reality" },
        F("spx", "SpiderX (spx)") { sec(it) == "reality" },
        F("pqv", "mldsa65Verify (pqv)") { sec(it) == "reality" }
    )

    private fun fieldsFor(proto: String): List<F> {
        val head = listOf(
            F("_name", "ملاحظات (نام)"),
            F("_addr", "نشانی سرور"),
            F("_port", "پورت", numeric = true)
        )
        return when (proto) {
            "vless" -> head + listOf(
                F("_id", "شناسه (UUID)"),
                F("encryption", "رمزنگاری (encryption)"),
                F(
                    "flow", "حالت جریان (flow)",
                    listOf(
                        "" to "بدون",
                        "xtls-rprx-vision" to "xtls-rprx-vision",
                        "xtls-rprx-vision-udp443" to "xtls-rprx-vision-udp443"
                    )
                )
            ) + transport(false)
            "trojan" -> head + listOf(F("_pass", "رمز عبور")) + transport(false)
            "vmess" -> head + listOf(
                F("_id", "شناسه (UUID)"),
                F("aid", "alterId", numeric = true),
                F("scy", "رمزنگاری", same("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"))
            ) + transport(true)
            else -> head + listOf(
                F("_pass", "رمز عبور (Auth)"),
                F("sni", "SNI"),
                F("insecure", "اجازه‌ی گواهی ناامن", listOf("0" to "خیر", "1" to "بله")),
                F("obfs", "مبهم‌سازی (obfs)", listOf("" to "بدون", "salamander" to "salamander")),
                F("obfs-password", "رمز obfs") { it["obfs"] == "salamander" },
                F("mport", "بازه‌ی پورت هاپینگ (مثلاً 20000-30000)"),
                F("pinSHA256", "pinSHA256")
            )
        }
    }

    private fun defaults(proto: String): Map<String, String> = when (proto) {
        "vless" -> linkedMapOf(
            "_name" to "کانفیگ جدید", "_port" to "443", "encryption" to "none",
            "type" to "tcp", "security" to "tls", "fp" to "chrome"
        )
        "trojan" -> linkedMapOf(
            "_name" to "کانفیگ جدید", "_port" to "443", "type" to "tcp",
            "security" to "tls", "fp" to "chrome"
        )
        "vmess" -> linkedMapOf(
            "_name" to "کانفیگ جدید", "_port" to "443", "aid" to "0", "scy" to "auto",
            "type" to "tcp", "security" to "none"
        )
        else -> linkedMapOf("_name" to "کانفیگ جدید", "_port" to "443")
    }

    // ---------- parse ----------

    private fun parse(link: String): Draft? {
        val l = link.trim()
        return try {
            when {
                l.startsWith("vmess://") -> parseVmess(l)
                l.startsWith("vless://") -> parseUri(l, "vless")
                l.startsWith("trojan://") -> parseUri(l, "trojan")
                l.startsWith("hysteria2://") || l.startsWith("hy2://") ->
                    parseUri(l.replaceFirst("hy2://", "hysteria2://"), "hysteria2")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUri(l: String, proto: String): Draft {
        val u = Uri.parse(l)
        val m = LinkedHashMap<String, String>()
        m["_name"] = u.fragment.orEmpty()
        m["_addr"] = (u.host ?: "").trim('[', ']')
        m["_port"] = if (u.port > 0) u.port.toString() else "443"
        val ui = u.userInfo.orEmpty()
        if (proto == "vless" || proto == "vmess") m["_id"] = ui else m["_pass"] = ui
        for (k in u.queryParameterNames) m[k] = u.getQueryParameter(k) ?: ""
        if (proto == "trojan" && !m.containsKey("security")) m["security"] = "tls"
        if (proto == "vmess") {
            m["scy"] = m["encryption"] ?: m["scy"] ?: "auto"
            m["aid"] = "0"
            m.remove("encryption")
        }
        return Draft(proto, m)
    }

    private fun parseVmess(l: String): Draft {
        val body = l.removePrefix("vmess://").substringBefore("#")
        val j = try {
            JSONObject(decodeBase64(body))
        } catch (e: Exception) {
            return parseUri(l, "vmess")
        }
        val m = LinkedHashMap<String, String>()
        m["_name"] = j.optString("ps")
        m["_addr"] = j.optString("add")
        m["_port"] = j.optString("port", "443")
        m["_id"] = j.optString("id")
        m["aid"] = j.optString("aid", "0").ifEmpty { "0" }
        m["scy"] = j.optString("scy", "auto").ifEmpty { "auto" }
        m["type"] = j.optString("net", "tcp").ifEmpty { "tcp" }
        m["headerType"] = j.optString("type", "none").ifEmpty { "none" }
        m["host"] = j.optString("host")
        m["path"] = j.optString("path")
        m["serviceName"] = j.optString("path")
        m["security"] = if (j.optString("tls") == "tls") "tls" else "none"
        m["sni"] = j.optString("sni")
        m["alpn"] = j.optString("alpn")
        m["fp"] = j.optString("fp")
        return Draft("vmess", m)
    }

    private fun decodeBase64(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT))
    }

    // ---------- build ----------

    private fun build(proto: String, m: Map<String, String>): String {
        val name = m["_name"].orEmpty().trim()
        val addr = m["_addr"].orEmpty().trim()
        if (addr.isEmpty()) throw IllegalArgumentException(trForError("نشانی سرور خالیه"))
        val port = m["_port"].orEmpty().trim().toIntOrNull()
            ?: throw IllegalArgumentException(trForError("پورت نامعتبره"))
        if (port !in 1..65535) throw IllegalArgumentException(trForError("پورت باید بین ۱ تا ۶۵۵۳۵ باشه"))
        return if (proto == "vmess") buildVmess(name, addr, port, m) else buildUri(proto, name, addr, port, m)
    }

    private fun buildUri(proto: String, name: String, addr: String, port: Int, m: Map<String, String>): String {
        val host = if (addr.contains(":") && !addr.startsWith("[")) "[$addr]" else addr
        val user = if (proto == "vless") {
            val id = m["_id"].orEmpty().trim()
            if (id.isEmpty()) throw IllegalArgumentException(trForError("شناسه (UUID) خالیه"))
            id
        } else {
            val p = m["_pass"].orEmpty()
            if (p.isEmpty()) throw IllegalArgumentException(trForError("رمز عبور خالیه"))
            Uri.encode(p)
        }
        val q = StringBuilder()
        for ((k, v0) in m) {
            if (k.startsWith("_")) continue
            val v = v0.trim()
            if (v.isEmpty()) continue
            if ((k == "allowInsecure" || k == "insecure") && v == "0") continue
            q.append(if (q.isEmpty()) "?" else "&")
                .append(Uri.encode(k)).append("=").append(Uri.encode(v))
        }
        return "$proto://$user@$host:$port$q" + (if (name.isNotEmpty()) "#" + Uri.encode(name) else "")
    }

    private fun buildVmess(name: String, addr: String, port: Int, m: Map<String, String>): String {
        val id = m["_id"].orEmpty().trim()
        if (id.isEmpty()) throw IllegalArgumentException("شناسه (UUID) خالیه")
        val net = (m["type"] ?: "tcp").ifEmpty { "tcp" }
        val j = JSONObject()
        j.put("v", "2")
        j.put("ps", name)
        j.put("add", addr)
        j.put("port", port.toString())
        j.put("id", id)
        j.put("aid", (m["aid"] ?: "0").ifEmpty { "0" })
        j.put("scy", (m["scy"] ?: "auto").ifEmpty { "auto" })
        j.put("net", net)
        j.put("type", if (net == "tcp") (m["headerType"] ?: "none").ifEmpty { "none" } else "none")
        j.put("host", m["host"].orEmpty())
        j.put("path", if (net == "grpc") m["serviceName"].orEmpty() else m["path"].orEmpty())
        j.put("tls", if (m["security"] == "tls") "tls" else "")
        j.put("sni", m["sni"].orEmpty())
        j.put("alpn", m["alpn"].orEmpty())
        j.put("fp", m["fp"].orEmpty())
        val b64 = Base64.encodeToString(j.toString().toByteArray(), Base64.NO_WRAP)
        return "vmess://$b64" + (if (name.isNotEmpty()) "#" + Uri.encode(name) else "")
    }

    // ---------- entry points ----------

    fun editConfig(act: AppCompatActivity, store: ConfigStore, cfg: Cfg, onDone: () -> Unit) {
        val d = parse(cfg.link)
        if (d == null) {
            Toast.makeText(act, tr(act, "این کانفیگ قابل ویرایش نیست"), Toast.LENGTH_LONG).show()
            return
        }
        showForm(act, d.protocol, d.params, "ویرایش کانفیگ (${d.protocol.uppercase()})") { link ->
            val i = store.items.indexOfFirst { it.id == cfg.id }
            if (i >= 0) {
                val name = ConfigStore.nameOf(link)
                store.items[i] = Cfg(cfg.id, link, name)
                store.rename(cfg.id, name)
                AppLog.add("کانفیگ ویرایش شد: $name")
                Toast.makeText(act, tr(act, "ذخیره شد. از اتصال بعدی اعمال می‌شه"), Toast.LENGTH_LONG).show()
                onDone()
            }
        }
    }

    fun createConfig(act: AppCompatActivity, store: ConfigStore, onDone: () -> Unit) {
        val names = arrayOf("VLESS", "VMESS", "TROJAN", "HYSTERIA2")
        val protos = arrayOf("vless", "vmess", "trojan", "hysteria2")
        AlertDialog.Builder(act)
            .setTitle("نوع کانفیگ")
            .setItems(names) { _, i ->
                showForm(act, protos[i], defaults(protos[i]), "کانفیگ جدید (${names[i]})") { link ->
                    store.add(link)
                    AppLog.add("کانفیگ دستی ساخته شد")
                    onDone()
                }
            }
            .show()
    }

    // ---------- form ----------

    private fun showForm(
        act: AppCompatActivity,
        proto: String,
        initial: Map<String, String>,
        title: String,
        onSave: (String) -> Unit
    ) {
        val fields = fieldsFor(proto)
        val density = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val dialog = BottomSheetDialog(act)
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(8))

        val titleTv = TextView(act)
        titleTv.text = title
        titleTv.textSize = 18f
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD)
        titleTv.setTextColor(ContextCompat.getColor(act, R.color.ink))
        titleTv.setPadding(0, 0, 0, dp(8))
        root.addView(titleTv)

        val sv = ScrollView(act)
        val form = LinearLayout(act)
        form.orientation = LinearLayout.VERTICAL
        sv.addView(form)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val getters = LinkedHashMap<String, () -> String>()
        val boxes = LinkedHashMap<String, View>()

        fun values(): Map<String, String> {
            val m = LinkedHashMap<String, String>(initial)
            fields.forEach { m[it.key] = getters[it.key]?.invoke().orEmpty() }
            return m
        }

        fun refresh() {
            val v = values()
            fields.forEach { boxes[it.key]?.visibility = if (it.show(v)) View.VISIBLE else View.GONE }
        }

        for (f in fields) {
            val box = LinearLayout(act)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(0, dp(6), 0, dp(2))
            val lab = TextView(act)
            lab.text = f.label
            lab.textSize = 12f
            lab.setTextColor(ContextCompat.getColor(act, R.color.ink_soft))
            box.addView(lab)
            val cur = initial[f.key].orEmpty()
            val fo = f.opts
            if (fo != null) {
                val o = fo.toMutableList()
                if (cur.isNotEmpty() && o.none { it.first == cur }) o.add(cur to cur)
                val sp = Spinner(act)
                sp.adapter = ArrayAdapter<String>(
                    act, android.R.layout.simple_spinner_dropdown_item, o.map { it.second }
                )
                val idx = o.indexOfFirst { it.first == cur }
                sp.setSelection(if (idx >= 0) idx else 0)
                sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        refresh()
                    }

                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
                getters[f.key] = { o[sp.selectedItemPosition].first }
                box.addView(sp)
            } else {
                val et = EditText(act)
                et.setText(cur)
                et.setSingleLine(!f.multi)
                if (f.multi) {
                    et.minLines = 3
                    et.gravity = Gravity.TOP
                }
                if (f.numeric) et.inputType = InputType.TYPE_CLASS_NUMBER
                getters[f.key] = { et.text.toString().trim() }
                box.addView(et)
            }
            boxes[f.key] = box
            form.addView(box)
        }

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(8), 0, 0)
        val cancel = MaterialButton(act)
        cancel.text = "لغو"
        val save = MaterialButton(act)
        save.text = "ذخیره"
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.setMargins(dp(4), 0, dp(4), 0)
        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp2.setMargins(dp(4), 0, dp(4), 0)
        row.addView(cancel, lp1)
        row.addView(save, lp2)
        root.addView(row)

        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val v = LinkedHashMap<String, String>(values())
            fields.forEach { if (!it.show(v)) v.remove(it.key) }
            try {
                val link = build(proto, v)
                ConfigParser.buildConfig(link)
                dialog.dismiss()
                onSave(link)
            } catch (e: Exception) {
                Toast.makeText(act, "نامعتبر: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }

        refresh()
        dialog.setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (act.resources.displayMetrics.heightPixels * 0.9).toInt()
            )
        )
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isDraggable = false
        dialog.show()
    }
}
