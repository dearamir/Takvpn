package com.takvpn.app

import android.content.Context
import android.net.Uri
import android.util.Base64
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

object SubFetcher {
    fun fetch(url: String): List<String> {
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
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
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
    }

    fun urls(): List<String> = map.keys.toList()

    fun count(url: String): Int = map[url]?.size ?: 0

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
        save()
    }

    private fun save() {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        prefs.edit().putString("subs", o.toString()).apply()
    }
}
