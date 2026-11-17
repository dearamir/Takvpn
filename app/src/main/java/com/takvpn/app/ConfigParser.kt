package com.takvpn.app

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object ConfigParser {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun buildConfig(link: String): String {
        val outbound = parseOutbound(link.trim())
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("dns", JSONObject().put("servers", JSONArray(listOf("localhost"))))
        root.put(
            "inbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "tun")
                    .put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500))
                    .put(
                        "sniffing", JSONObject()
                            .put("enabled", true)
                            .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                    )
            )
        )
        root.put(
            "outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        )
        return root.toString()
    }

    private fun parseOutbound(link: String): JSONObject = when {
        link.startsWith("vless://") -> vless(link)
        link.startsWith("vmess://") -> vmess(link)
        link.startsWith("trojan://") -> trojan(link)
        link.startsWith("hysteria2://") || link.startsWith("hy2://") -> hysteria2(link)
        else -> throw IllegalArgumentException("این پروتکل پشتیبانی نمی‌شه")
    }

    private fun vless(link: String): JSONObject {
        val u = Uri.parse(link)
        val host = cleanHost(u)
        val port = if (u.port > 0) u.port else 443
        val id = u.userInfo ?: throw IllegalArgumentException("UUID پیدا نشد")
        val q = query(u)
        val user = JSONObject().put("id", id).put("encryption", q["encryption"] ?: "none")
        q["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        val server = JSONObject()
            .put("address", host).put("port", port)
            .put("users", JSONArray().put(user))
        return outbound("vless", JSONObject().put("vnext", JSONArray().put(server)), stream(q, host))
    }

    private fun vmess(link: String): JSONObject {
        val body = link.removePrefix("vmess://").substringBefore("#")
        val j = try {
            JSONObject(decodeBase64(body))
        } catch (e: Exception) {
            return vmessUri(link)
        }
        val host = j.getString("add")
        val port = j.optString("port", "443").toInt()
        val net = j.optString("net", "tcp").ifEmpty { "tcp" }
        val q = mapOf(
            "type" to net,
            "headerType" to j.optString("type"),
            "host" to j.optString("host"),
            "path" to j.optString("path"),
            "serviceName" to j.optString("path"),
            "security" to (if (j.optString("tls") == "tls") "tls" else "none"),
            "sni" to j.optString("sni"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp")
        )
        val user = JSONObject()
            .put("id", j.getString("id"))
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
        val server = JSONObject()
            .put("address", host).put("port", port)
            .put("users", JSONArray().put(user))
        return outbound("vmess", JSONObject().put("vnext", JSONArray().put(server)), stream(q, host))
    }

    private fun vmessUri(link: String): JSONObject {
        val u = Uri.parse(link)
        val host = cleanHost(u)
        val port = if (u.port > 0) u.port else 443
        val id = u.userInfo ?: throw IllegalArgumentException("UUID پیدا نشد")
        val q = query(u)
        val user = JSONObject()
            .put("id", id)
            .put("alterId", 0)
            .put("security", q["encryption"] ?: q["scy"] ?: "auto")
        val server = JSONObject()
            .put("address", host).put("port", port)
            .put("users", JSONArray().put(user))
        return outbound("vmess", JSONObject().put("vnext", JSONArray().put(server)), stream(q, host))
    }

    private fun trojan(link: String): JSONObject {
        val u = Uri.parse(link)
        val host = cleanHost(u)
        val port = if (u.port > 0) u.port else 443
        val password = u.userInfo ?: throw IllegalArgumentException("پسورد پیدا نشد")
        val q = query(u).toMutableMap()
        if (!q.containsKey("security")) q["security"] = "tls"
        val server = JSONObject()
            .put("address", host).put("port", port).put("password", password)
        return outbound("trojan", JSONObject().put("servers", JSONArray().put(server)), stream(q, host))
    }

    private fun hysteria2(link: String): JSONObject {
        val u = Uri.parse(link.replaceFirst("hy2://", "hysteria2://"))
        val host = cleanHost(u)
        val port = if (u.port > 0) u.port else 443
        val auth = u.userInfo ?: throw IllegalArgumentException("رمز Hysteria پیدا نشد")
        val q = query(u)
        if (!q["obfs"].isNullOrEmpty()) {
            throw IllegalArgumentException("obfs هنوز پشتیبانی نمی‌شه")
        }
        val insecure = q["insecure"] == "1" || q["insecure"] == "true"
        val sni = q["sni"].orEmpty().ifEmpty { host }
        val tls = JSONObject()
            .put("serverName", sni)
            .put("alpn", JSONArray(listOf("h3")))
            .put("allowInsecure", insecure)
        val stream = JSONObject()
            .put("network", "hysteria")
            .put("method", "hysteria")
            .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", auth))
            .put("security", "tls")
            .put("tlsSettings", tls)
        val settings = JSONObject().put("version", 2).put("address", host).put("port", port)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "hysteria")
            .put("settings", settings)
            .put("streamSettings", stream)
    }

    private fun outbound(protocol: String, settings: JSONObject, stream: JSONObject) =
        JSONObject()
            .put("tag", "proxy")
            .put("protocol", protocol)
            .put("settings", settings)
            .put("streamSettings", stream)

    private fun stream(q: Map<String, String>, address: String): JSONObject {
        val type = q["type"] ?: "tcp"
        val net = if (type == "splithttp") "xhttp" else type
        val host = q["host"].orEmpty()
        val path = q["path"].orEmpty().ifEmpty { "/" }
        val s = JSONObject().put("network", net)

        when (net) {
            "tcp", "raw" -> if (q["headerType"] == "http") {
                val hosts = host.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf(address) }
                val paths = path.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("/") }
                val headers = JSONObject()
                    .put("Host", JSONArray(hosts))
                    .put("User-Agent", JSONArray(listOf(UA)))
                    .put("Accept-Encoding", JSONArray(listOf("gzip, deflate")))
                    .put("Connection", JSONArray(listOf("keep-alive")))
                    .put("Pragma", "no-cache")
                val request = JSONObject()
                    .put("version", "1.1")
                    .put("method", "GET")
                    .put("path", JSONArray(paths))
                    .put("headers", headers)
                val raw = JSONObject()
                    .put("header", JSONObject().put("type", "http").put("request", request))
                s.put("tcpSettings", raw)
                s.put("rawSettings", JSONObject(raw.toString()))
            }
            "ws" -> {
                val ws = JSONObject().put("path", path)
                if (host.isNotEmpty()) ws.put("headers", JSONObject().put("Host", host))
                s.put("wsSettings", ws)
            }
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", q["serviceName"].orEmpty()))
            "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", path).put("host", host))
            "xhttp" -> {
                val xs = JSONObject()
                    .put("path", path)
                    .put("host", host)
                    .put("mode", q["mode"] ?: "auto")
                val extra = q["extra"].orEmpty()
                if (extra.isNotBlank()) {
                    try {
                        xs.put("extra", JSONObject(extra))
                    } catch (e: Exception) {
                    }
                }
                s.put("xhttpSettings", xs)
            }
        }

        val security = q["security"] ?: "none"
        val sni = q["sni"].orEmpty()
        val fp = q["fp"].orEmpty().ifEmpty { "chrome" }
        s.put("security", security)

        when (security) {
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", sni.ifEmpty { host.ifEmpty { address } })
                    .put("fingerprint", fp)
                val alpn = q["alpn"].orEmpty()
                if (alpn.isNotEmpty()) tls.put("alpn", JSONArray(alpn.split(",")))
                val insecure = q["allowInsecure"] == "1" || q["allowInsecure"] == "true" ||
                    q["insecure"] == "1" || q["insecure"] == "true"
                if (insecure) tls.put("allowInsecure", true)
                s.put("tlsSettings", tls)
            }
            "reality" -> {
                val rs = JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", fp)
                    .put("publicKey", q["pbk"].orEmpty())
                    .put("shortId", q["sid"].orEmpty())
                    .put("spiderX", q["spx"].orEmpty())
                val pqv = q["pqv"].orEmpty()
                if (pqv.isNotEmpty()) rs.put("mldsa65Verify", pqv)
                s.put("realitySettings", rs)
            }
        }
        return s
    }

    private fun query(u: Uri): Map<String, String> =
        u.queryParameterNames.associateWith { u.getQueryParameter(it) ?: "" }

    private fun cleanHost(u: Uri): String =
        (u.host ?: throw IllegalArgumentException("آدرس سرور پیدا نشد")).trim('[', ']')

    private fun decodeBase64(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT))
    }
}
