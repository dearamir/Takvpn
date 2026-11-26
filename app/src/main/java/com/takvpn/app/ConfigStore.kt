package com.takvpn.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Cfg(val id: String, val link: String, var name: String)

class ConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("takvpn", Context.MODE_PRIVATE)
    val items = mutableListOf<Cfg>()
    var selectedId: String = ""

    init {
        try {
            val arr = JSONArray(prefs.getString("configs", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Cfg(o.getString("id"), o.getString("link"), o.getString("name")))
            }
        } catch (e: Exception) {
            items.clear()
        }
        selectedId = prefs.getString("selected", "") ?: ""
        if (items.none { it.id == selectedId }) selectedId = items.firstOrNull()?.id ?: ""
    }

    fun selected(): Cfg? = items.firstOrNull { it.id == selectedId }

    fun add(link: String) {
        val c = Cfg(UUID.randomUUID().toString(), link, nameOf(link))
        items.add(c)
        if (selectedId.isEmpty()) selectedId = c.id
        save()
    }

    fun select(id: String) {
        selectedId = id
        save()
    }

    fun rename(id: String, name: String) {
        items.firstOrNull { it.id == id }?.name = name
        save()
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        if (selectedId == id) selectedId = items.firstOrNull()?.id ?: ""
        save()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("link", it.link).put("name", it.name))
        }
        prefs.edit().putString("configs", arr.toString()).putString("selected", selectedId).apply()
    }

    companion object {
        fun typeOf(link: String): String {
            val uri = Uri.parse(link.trim())
            val scheme = uri.scheme?.lowercase().orEmpty()
            var protocol = when (scheme) {
                "vless" -> "VLESS"
                "vmess" -> "VMess"
                "hysteria" -> "Hysteria v1 (پشتیبانی نمی‌شود)"
                "hysteria2", "hy2" -> "Hysteria 2"
                "trojan" -> "Trojan"
                "ss" -> "Shadowsocks"
                "ssh" -> "SSH"
                "sn" -> if (uri.host.equals("ssh", ignoreCase = true) ||
                    uri.path.equals("/ssh", ignoreCase = true)) "SSH" else "نامشخص"
                else -> "نامشخص"
            }
            if (protocol == "SSH") return protocol

            var transport = uri.getQueryParameter("type").orEmpty()
            var security = uri.getQueryParameter("security").orEmpty()
            var host = uri.getQueryParameter("host").orEmpty()
            var path = uri.getQueryParameter("path").orEmpty()
            var sni = uri.getQueryParameter("sni").orEmpty()
            var flow = uri.getQueryParameter("flow").orEmpty()
            if (scheme == "vmess") {
                try {
                    var encoded = link.trim().removePrefix("vmess://").substringBefore("#")
                        .replace('-', '+').replace('_', '/')
                    while (encoded.length % 4 != 0) encoded += "="
                    val data = JSONObject(String(Base64.decode(encoded, Base64.DEFAULT)))
                    transport = data.optString("net", transport)
                    host = data.optString("host", host)
                    path = data.optString("path", path)
                    sni = data.optString("sni", sni)
                    security = if (data.optString("tls").isNotBlank() && data.optString("tls") != "none") "tls" else security
                } catch (_: Exception) { }
            }
            val parts = mutableListOf(protocol)
            val transportLabel = when (transport.lowercase()) {
                "tcp" -> "TCP"
                "ws" -> "WS"
                "grpc" -> "gRPC"
                "xhttp", "splithttp" -> "XHTTP"
                "httpupgrade" -> "HTTPUpgrade"
                "kcp" -> "mKCP"
                "quic" -> "QUIC"
                "http" -> "HTTP"
                else -> transport.takeIf { it.isNotBlank() }?.uppercase()
            }
            transportLabel?.let { parts.add(it) }
            when (security.lowercase()) {
                "reality" -> parts.add("REALITY")
                "tls" -> parts.add("TLS")
                "none" -> Unit
                else -> security.takeIf { it.isNotBlank() }?.uppercase()?.let { parts.add(it) }
            }
            if (flow.isNotBlank()) parts.add(flow.uppercase())
            val cdnLike = transport.lowercase() in listOf("ws", "httpupgrade") &&
                host.isNotBlank() && security.equals("tls", ignoreCase = true)
            if (cdnLike) parts.add("CDN")
            return parts.distinct().joinToString(" / ")
        }

        fun nameOf(l: String): String {
            val name = try {
                if (l.startsWith("vmess://")) {
                    var t = l.removePrefix("vmess://").substringBefore("#")
                        .trim().replace('-', '+').replace('_', '/')
                    while (t.length % 4 != 0) t += "="
                    JSONObject(String(Base64.decode(t, Base64.DEFAULT))).optString("ps")
                } else {
                    Uri.decode(l.substringAfter("#", ""))
                }
            } catch (e: Exception) {
                ""
            }
            return name.ifBlank {
                if (l.trim().startsWith("sn://ssh?", ignoreCase = true) ||
                    l.trim().startsWith("ssh://", ignoreCase = true)) "SSH" else "کانفیگ بدون نام"
            }
        }
    }
}
