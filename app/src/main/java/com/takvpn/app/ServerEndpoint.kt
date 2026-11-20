package com.takvpn.app

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/**
 * Extracts the remote endpoint from the raw share link of the currently active profile.
 * This deliberately does not inspect the phone's public/egress IP.
 */
internal object ServerEndpoint {
    fun hostFromLink(link: String): String? {
        val value = link.trim()
        if (value.startsWith("vmess://", ignoreCase = true)) {
            val payload = value.substringAfter("://").substringBefore('#').trim()
            val decoded = decode(payload) ?: return null
            return try {
                JSONObject(decoded).optString("add").trim().takeIf { it.isNotEmpty() }
            } catch (_: Throwable) {
                null
            }
        }
        return try {
            val uri = Uri.parse(value)
            uri.host?.trim()?.trim('[', ']')?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    
    fun addressPortFromLink(link: String): String? {
        val value = link.trim()
        return try {
            if (value.startsWith("vmess://", ignoreCase = true)) {
                val payload = value.substringAfter("://").substringBefore('#').trim()
                val decoded = decode(payload) ?: return null
                val json = JSONObject(decoded)
                val host = json.optString("add").trim().takeIf { it.isNotEmpty() } ?: return null
                val portValue = json.opt("port")
                val port = when (portValue) {
                    is Number -> portValue.toInt()
                    else -> portValue?.toString()?.toIntOrNull()
                } ?: return host
                formatAddressPort(host, port)
            } else {
                val uri = Uri.parse(value)
                val host = uri.host?.trim()?.trim('[', ']')?.takeIf { it.isNotEmpty() } ?: return null
                val port = uri.port
                if (port > 0) formatAddressPort(host, port) else host
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatAddressPort(host: String, port: Int): String {
        val displayHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$displayHost : $port"
    }

    private fun decode(value: String): String? {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return try {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Throwable) {
            try {
                String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
