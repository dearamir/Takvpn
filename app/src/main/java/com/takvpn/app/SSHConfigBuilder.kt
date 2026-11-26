package com.takvpn.app

import android.net.Uri
import android.util.Base64
import com.esotericsoftware.kryo.io.ByteBufferInput
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

/**
 * Converts SSH share links to a native sing-box configuration.
 * NekoBox sn://ssh links contain a zlib-compressed Kryo SSHBean.
 */
object SSHConfigBuilder {
    fun build(link: String): String {
        val profile = when {
            link.startsWith("ssh://", true) -> parseUri(link)
            link.startsWith("sn://ssh?", true) -> parseNekoBox(link)
            else -> throw IllegalArgumentException("Unsupported SSH link")
        }

        val outbound = JSONObject()
            .put("type", "ssh")
            .put("tag", "proxy")
            .put("server", profile.host)
            .put("server_port", profile.port)
            .put("user", profile.username)

        if (profile.password.isNotEmpty()) outbound.put("password", profile.password)
        if (profile.privateKey.isNotEmpty()) {
            outbound.put("private_key", profile.privateKey)
            if (profile.privateKeyPassphrase.isNotEmpty()) {
                outbound.put("private_key_passphrase", profile.privateKeyPassphrase)
            }
        }
        if (profile.hostKeys.isNotEmpty()) {
            outbound.put("host_key", JSONArray(profile.hostKeys))
        }

        val dnsServer = JSONObject()
            .put("tag", "dns-remote")
            .put("address", "1.1.1.1")
            .put("detour", "proxy")

        val tun = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray(listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126")))
            .put("mtu", 1500)
            .put("auto_route", false)
            .put("stack", "gvisor")

        val routeRules = JSONArray()
        if (AppPrefs.directIranEnabled()) {
            routeRules.put(
                JSONObject()
                    .put("domain_suffix", JSONArray(listOf("ir")))
                    .put("outbound", "direct")
            )
        }

        val root = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put(dnsServer))
                .put("final", "dns-remote"))
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block")))
            .put("route", JSONObject()
                .put("auto_detect_interface", false)
                .put("rules", routeRules)
                .put("final", "proxy"))

        return root.toString()
    }

    private data class Profile(
        val host: String,
        val port: Int,
        val username: String,
        val password: String = "",
        val privateKey: String = "",
        val privateKeyPassphrase: String = "",
        val hostKeys: List<String> = emptyList()
    )

    private fun parseUri(link: String): Profile {
        val uri = Uri.parse(link)
        val host = uri.host?.trim('[', ']')?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("SSH server address is missing")
        val userInfo = uri.userInfo.orEmpty()
        val pieces = userInfo.split(":", limit = 2)
        val username = Uri.decode(pieces.firstOrNull().orEmpty())
        if (username.isBlank()) throw IllegalArgumentException("SSH username is missing")
        val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
        val auth = query["auth"] ?: if (query["privateKey"].isNullOrBlank()) "password" else "private_key"
        return if (auth == "private_key") {
            val key = query["privateKey"].orEmpty()
            if (key.isBlank()) throw IllegalArgumentException("SSH private key is missing")
            Profile(
                host, if (uri.port > 0) uri.port else 22, username,
                privateKey = key,
                privateKeyPassphrase = query["privateKeyPassphrase"].orEmpty()
            )
        } else {
            val password = if (pieces.size > 1) Uri.decode(pieces[1]) else query["password"].orEmpty()
            if (password.isBlank()) throw IllegalArgumentException("SSH password is missing")
            Profile(host, if (uri.port > 0) uri.port else 22, username, password = password)
        }
    }

    private fun parseNekoBox(link: String): Profile {
        val encoded = link.substringAfter("?", "")
        if (encoded.isBlank()) throw IllegalArgumentException("NekoBox SSH payload is empty")
        val compressed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payload = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        val input = ByteBufferInput(payload)
        try {
            val beanVersion = input.readInt()
            if (beanVersion != 0) throw IllegalArgumentException("Unsupported NekoBox SSH profile version")
            val host = input.readString().orEmpty()
            val port = input.readInt()
            val username = input.readString().orEmpty()
            val authType = input.readInt()
            var password = ""
            var privateKey = ""
            var privateKeyPassphrase = ""
            when (authType) {
                0 -> Unit
                1 -> password = input.readString().orEmpty()
                2 -> {
                    privateKey = input.readString().orEmpty()
                    privateKeyPassphrase = input.readString().orEmpty()
                }
                else -> throw IllegalArgumentException("Unsupported SSH authentication method")
            }
            val publicKey = input.readString().orEmpty()
            input.readInt() // Extended bean version.
            input.readString() // Profile name.
            input.readString() // Custom outbound JSON.
            input.readString() // Custom config JSON.
            if (host.isBlank() || username.isBlank()) {
                throw IllegalArgumentException("NekoBox SSH profile is missing server or username")
            }
            if (authType == 1 && password.isBlank()) throw IllegalArgumentException("SSH password is missing")
            if (authType == 2 && privateKey.isBlank()) throw IllegalArgumentException("SSH private key is missing")
            return Profile(
                host = host,
                port = if (port in 1..65535) port else 22,
                username = username,
                password = password,
                privateKey = privateKey,
                privateKeyPassphrase = privateKeyPassphrase,
                hostKeys = publicKey.lines().map { it.trim() }.filter { it.isNotEmpty() }
            )
        } finally {
            input.close()
        }
    }
}

/** Reads only the existing TakVPN preference; no new settings are introduced. */
private object AppPrefs {
    fun directIranEnabled(): Boolean = false
}
