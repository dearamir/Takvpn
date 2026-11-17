package com.takvpn.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import android.view.animation.LinearInterpolator
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConfigExtras {
    @Volatile
    var logPath: String? = null

    @Volatile
    var hopEnabled = true

    private val supported = listOf("vless://", "vmess://", "trojan://", "hysteria2://", "hy2://")

    fun isSupported(s: String) = supported.any { s.startsWith(it) }

    fun build(link: String): String {
        val root = JSONObject(ConfigParser.buildConfig(link))
        logPath?.let { root.getJSONObject("log").put("error", it) }
        if (hopEnabled && (link.startsWith("hysteria2://") || link.startsWith("hy2://"))) {
            val mport = try {
                Uri.parse(link.replaceFirst("hy2://", "hysteria2://")).getQueryParameter("mport")
            } catch (e: Exception) {
                null
            }
            if (!mport.isNullOrBlank()) {
                val ob = root.getJSONArray("outbounds").getJSONObject(0)
                val st = ob.getJSONObject("streamSettings")
                st.getJSONObject("hysteriaSettings")
                    .put("udphop", JSONObject().put("port", mport).put("interval", "30s"))
                val fm = st.optJSONObject("finalmask") ?: JSONObject()
                val qp = fm.optJSONObject("quicParams") ?: JSONObject()
                if (!qp.has("udpHop")) {
                    qp.put("udpHop", JSONObject().put("ports", mport).put("interval", "5-10"))
                }
                fm.put("quicParams", qp)
                st.put("finalmask", fm)
            }
        }
        return root.toString()
    }
}

object AppLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        lines.addLast("${fmt.format(Date())}  $msg")
        while (lines.size > 300) lines.removeFirst()
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
    }

    fun coreLog(maxBytes: Int = 8000): String {
        val p = ConfigExtras.logPath ?: return ""
        val f = File(p)
        if (!f.exists()) return ""
        return try {
            val len = f.length()
            f.inputStream().use { s ->
                if (len > maxBytes) s.skip(len - maxBytes)
                String(s.readBytes())
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun clearCore() {
        ConfigExtras.logPath?.let {
            try {
                File(it).writeText("")
            } catch (e: Exception) {
            }
        }
    }
}

class SubInfo(val up: Long, val down: Long, val total: Long, val expire: Long) {
    fun toJson(): JSONObject =
        JSONObject().put("up", up).put("down", down).put("total", total).put("expire", expire)

    companion object {
        fun fromJson(o: JSONObject) =
            SubInfo(o.optLong("up"), o.optLong("down"), o.optLong("total"), o.optLong("expire"))

        fun parse(h: String?): SubInfo? {
            if (h.isNullOrBlank()) return null
            val m = HashMap<String, Long>()
            h.split(";").forEach { part ->
                val kv = part.trim().split("=")
                if (kv.size == 2) {
                    kv[1].trim().toDoubleOrNull()?.let { m[kv[0].trim().lowercase()] = it.toLong() }
                }
            }
            if (!m.containsKey("total") && !m.containsKey("expire")) return null
            return SubInfo(m["upload"] ?: 0L, m["download"] ?: 0L, m["total"] ?: 0L, m["expire"] ?: 0L)
        }
    }
}

class SubResult(val links: List<String>, val info: SubInfo?)

object SubFetcher {
    fun fetch(url: String): SubResult {
        val u = URL(url)
        if (u.protocol != "http" && u.protocol != "https") {
            throw IOException("آدرس باید با http یا https شروع بشه")
        }
        val c = u.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "v2rayNG/1.10.0")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val info = SubInfo.parse(c.getHeaderField("Subscription-Userinfo"))
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return SubResult(parse(body), info)
        } finally {
            c.disconnect()
        }
    }

    fun parse(body: String): List<String> {
        var text = body.trim()
        if (!text.contains("://")) {
            text = try {
                decode(text)
            } catch (e: Exception) {
                ""
            }
        }
        return text.split(Regex("(?=\\b(?:vless|vmess|trojan|hysteria2|hysteria|hy2|ss|tuic)://)"))
            .map { it.trim() }
            .filter { ConfigExtras.isSupported(it) }
            .distinct()
    }

    private fun decode(s: String): String {
        var t = s.replace("\n", "").replace("\r", "").replace(" ", "")
            .replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT))
    }
}

class SubStore(context: Context, private val store: ConfigStore) {
    private val prefs = context.getSharedPreferences("takvpn", Context.MODE_PRIVATE)
    private val map = LinkedHashMap<String, MutableList<String>>()
    private val infos = HashMap<String, SubInfo>()

    init {
        try {
            val o = JSONObject(prefs.getString("subs", "{}"))
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = o.getJSONArray(k)
                val ids = mutableListOf<String>()
                for (i in 0 until arr.length()) ids.add(arr.getString(i))
                map[k] = ids
            }
        } catch (e: Exception) {
            map.clear()
        }
        try {
            val o = JSONObject(prefs.getString("subinfo", "{}"))
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                infos[k] = SubInfo.fromJson(o.getJSONObject(k))
            }
        } catch (e: Exception) {
            infos.clear()
        }
    }

    fun urls(): List<String> = map.keys.toList()

    fun count(url: String): Int = map[url]?.size ?: 0

    fun setInfo(url: String, info: SubInfo) {
        infos[url] = info
        saveInfo()
    }

    fun infoForConfig(id: String): SubInfo? {
        for ((u, ids) in map) {
            if (id in ids) return infos[u]
        }
        return null
    }

    fun apply(url: String, links: List<String>) {
        val oldIds = map[url].orEmpty()
        val oldNames = HashMap<String, String>()
        store.items.filter { it.id in oldIds }.forEach { oldNames[it.link] = it.name }
        val selLink = store.selected()?.link
        oldIds.forEach { store.remove(it) }
        val newIds = mutableListOf<String>()
        links.forEach { link ->
            store.add(link)
            val c = store.items.last()
            oldNames[link]?.let { store.rename(c.id, it) }
            newIds.add(c.id)
        }
        map[url] = newIds
        if (selLink != null) {
            store.items.firstOrNull { it.link == selLink }?.let { store.select(it.id) }
        }
        save()
    }

    fun remove(url: String) {
        map[url]?.toList()?.forEach { store.remove(it) }
        map.remove(url)
        infos.remove(url)
        save()
        saveInfo()
    }

    private fun save() {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        prefs.edit().putString("subs", o.toString()).apply()
    }

    private fun saveInfo() {
        val o = JSONObject()
        infos.forEach { (k, v) -> o.put(k, v.toJson()) }
        prefs.edit().putString("subinfo", o.toString()).apply()
    }
}

object UpdateChecker {
    const val REPO = "dearamir/Takvpn"

    class Result(val tag: String, val minor: Int, val url: String)

    fun lastNumber(s: String): Int =
        Regex("(\\d+)\\D*$").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun latest(): Result {
        val u = URL("https://api.github.com/repos/$REPO/releases/latest")
        val c = u.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "Takvpn")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            return Result(
                tag,
                lastNumber(tag),
                o.optString("html_url", "https://github.com/$REPO/releases/latest")
            )
        } finally {
            c.disconnect()
        }
    }
}

class GlowAnimator(private val tv: TextView, private val base: Int, private val shine: Int) {
    private var anim: ValueAnimator? = null

    fun start() {
        if (anim != null) return
        val a = ValueAnimator.ofFloat(-0.5f, 1.5f)
        a.duration = 2000
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            val w = tv.width.toFloat().coerceAtLeast(1f)
            val p = it.animatedValue as Float
            val x = p * w
            tv.paint.shader = LinearGradient(
                x - w * 0.35f, 0f, x + w * 0.35f, 0f,
                intArrayOf(base, shine, base),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            tv.invalidate()
        }
        anim = a
        tv.setShadowLayer(16f, 0f, 0f, base)
        a.start()
    }

    fun stop(restoreColor: Int) {
        anim?.cancel()
        anim = null
        tv.paint.shader = null
        tv.setShadowLayer(0f, 0f, 0f, 0)
        tv.setTextColor(restoreColor)
        tv.invalidate()
    }
}
