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
        if (value.isEmpty()) return null
        return try {
            if (value.startsWith("vmess://", ignoreCase = true)) {
                val payload = value.substringAfter("://").substringBefore('#').trim()
                val decoded = decode(payload) ?: return null
                val json = JSONObject(decoded)
                val host = json.optString("add").trim().takeIf { it.isNotEmpty() } ?: return null
                val port = json.opt("port")?.toString()?.toIntOrNull()
                if (port != null && port in 1..65535) formatAddressPort(host, port) else host
            } else {
                // Parse the authority ourselves as Android Uri.host can be null for
                // imported links containing unusual but valid user-info/encoding.
                val afterScheme = value.substringAfter("://", "")
                if (afterScheme.isEmpty()) return null
                val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                val hostPort = authority.substringAfterLast('@', authority).trim()
                if (hostPort.isEmpty()) return null

                val host: String
                val port: Int?
                if (hostPort.startsWith("[")) {
                    val close = hostPort.indexOf(']')
                    if (close <= 1) return null
                    host = hostPort.substring(1, close)
                    port = hostPort.substring(close + 1).removePrefix(":").toIntOrNull()
                } else {
                    val colon = hostPort.lastIndexOf(':')
                    if (colon > 0 && hostPort.indexOf(':') == colon) {
                        host = hostPort.substring(0, colon)
                        port = hostPort.substring(colon + 1).toIntOrNull()
                    } else {
                        host = hostPort
                        port = null
                    }
                }
                if (host.isBlank()) return null
                if (port != null && port in 1..65535) formatAddressPort(host, port) else host
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatAddressPort(host: String, port: Int): String {
        val displayHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$displayHost:$port"
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
