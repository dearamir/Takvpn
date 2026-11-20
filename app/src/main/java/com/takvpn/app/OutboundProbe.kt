package com.takvpn.app

import android.content.Context
import android.os.SystemClock
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL

/**
 * Tests one profile in an isolated Xray instance. StartLoop(..., 0) deliberately
 * starts Xray without a TUN descriptor, so the user's active Android VPN is not
 * replaced or interrupted. A private SOCKS inbound sends the probe through only
 * the tested profile's proxy outbound.
 */
object OutboundProbe {
    data class Result(val latencyMs: Long, val exitIp: String)

    @Synchronized
    fun test(context: Context, link: String): Result {
        CoreEnv.init(context.applicationContext)
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use {
            it.localPort
        }

        val config = JSONObject(ConfigParser.buildConfig(link))
        config.remove("inbounds")
        val inbounds = JSONArray().put(
            JSONObject()
                .put("tag", "takvpn-isolated-probe")
                .put("listen", "127.0.0.1")
                .put("port", port)
                .put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "noauth").put("udp", false))
        )
        config.put("inbounds", inbounds)

        val routing = config.optJSONObject("routing") ?: JSONObject()
        val oldRules = routing.optJSONArray("rules") ?: JSONArray()
        val rules = JSONArray().put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("takvpn-isolated-probe"))
                .put("outboundTag", "proxy")
        )
        for (i in 0 until oldRules.length()) {
            val rule = oldRules.optJSONObject(i) ?: continue
            val tags = rule.optJSONArray("inboundTag")
            val isProbeRule = tags != null &&
                (0 until tags.length()).any { tags.optString(it) == "takvpn-isolated-probe" }
            if (!isProbeRule) rules.put(rule)
        }
        routing.put("rules", rules)
        config.put("routing", routing)

        val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, msg: String?): Long = 0
        })

        var connection: HttpURLConnection? = null
        try {
            // A zero TUN fd is explicitly supported by AndroidLibXrayLite.
            controller.startLoop(config.toString(), 0)
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress("127.0.0.1", port)
            )
            connection = (URL("https://api.ipify.org").openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                useCaches = false
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            val started = SystemClock.elapsedRealtime()
            val ip = connection.inputStream.bufferedReader().use { it.readText().trim() }
            val latency = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
            require(ip.isNotBlank() && ip.length <= 45 && ip.all {
                it.isDigit() || it in "abcdefABCDEF:."
            }) { "Exit IP نامعتبر دریافت شد" }
            return Result(latency, ip)
        } finally {
            try { connection?.disconnect() } catch (_: Throwable) {}
            try { controller.stopLoop() } catch (_: Throwable) {}
        }
    }
}
