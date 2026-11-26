package com.takvpn.app

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import java.lang.reflect.Proxy

/**
 * Runs sing-box only for SSH profiles. Xray remains the engine for all
 * existing Xray-compatible protocols.
 */
internal class SingBoxRunner(private val vpn: TakVpnService) {
    private var server: CommandServer? = null
    private var tun: ParcelFileDescriptor? = null

    @Synchronized
    fun start(config: String) {
        stop()
        val setup = SetupOptions().apply {
            basePath = vpn.filesDir.absolutePath
            workingPath = (vpn.getExternalFilesDir(null) ?: vpn.filesDir).absolutePath
            tempPath = vpn.cacheDir.absolutePath
            debug = false
            logMaxLines = 300
        }
        Libbox.setup(setup)

        val platform = Proxy.newProxyInstance(
            PlatformInterface::class.java.classLoader,
            arrayOf(PlatformInterface::class.java)
        ) { _, method, args ->
            when (method.name) {
                "usePlatformAutoDetectInterfaceControl" -> true
                "autoDetectInterfaceControl" -> {
                    val fd = (args?.getOrNull(0) as? Int) ?: -1
                    if (fd >= 0 && !vpn.protect(fd)) {
                        throw IllegalStateException("Android failed to protect sing-box socket")
                    }
                    null
                }
                "openTun" -> openTun(args?.getOrNull(0) as TunOptions)
                "getInterfaces" -> emptyIterator(NetworkInterfaceIterator::class.java)
                "startDefaultInterfaceMonitor", "closeDefaultInterfaceMonitor",
                "clearDNSCache", "registerMyInterface", "startNeighborMonitor",
                "closeNeighborMonitor" -> null
                "underNetworkExtension", "includeAllNetworks" -> false
                "readWIFIState", "localDNSTransport" -> null
                "usePlatformShell", "usePlatformBridge", "usePlatformAutoRedirect" -> false
                "toString" -> "TakVPN sing-box Android platform"
                "hashCode" -> System.identityHashCode(vpn)
                "equals" -> args?.firstOrNull() === vpn
                else -> defaultValue(method.returnType)
            }
        } as PlatformInterface

        val handler = Proxy.newProxyInstance(
            CommandServerHandler::class.java.classLoader,
            arrayOf(CommandServerHandler::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "serviceStop", "serviceReload", "setSystemProxyEnabled" -> null
                "getSystemProxyStatus" -> null
                "toString" -> "TakVPN sing-box handler"
                else -> defaultValue(method.returnType)
            }
        } as CommandServerHandler

        val commandServer = CommandServer(handler, platform)
        commandServer.start()
        try {
            commandServer.startOrReloadService(config, OverrideOptions())
            server = commandServer
        } catch (e: Throwable) {
            runCatching { commandServer.closeService() }
            runCatching { commandServer.close() }
            tun?.close()
            tun = null
            throw e
        }
    }

    private fun openTun(options: TunOptions): Int {
        val builder = VpnService.Builder(vpn)
            .setSession("TakVPN SSH")
            .setMtu(options.mtu.coerceIn(1280, 9000))

        val v4 = options.inet4Address
        while (v4.hasNext()) {
            val address = v4.next()
            builder.addAddress(address.address(), address.prefix())
        }
        val v6 = options.inet6Address
        while (v6.hasNext()) {
            val address = v6.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val dns = options.dnsServerAddress
        while (dns.hasNext()) {
            builder.addDnsServer(dns.next())
        }

        // Routes are installed by Android's VpnService. The sing-box TUN
        // inbound is configured with auto_route=false to avoid duplicate routes.
        builder.addRoute("0.0.0.0", 0)
        runCatching { builder.addRoute("::", 0) }

        val prefs = vpn.getSharedPreferences("takvpn", android.content.Context.MODE_PRIVATE)
        val mode = prefs.getInt("split_mode", 0)
        val apps = prefs.getStringSet("split_apps", emptySet()) ?: emptySet()
        var allowed = 0
        if (mode == 1 && apps.isNotEmpty()) {
            apps.forEach { name ->
                runCatching { builder.addAllowedApplication(name); allowed++ }
            }
        }
        if (allowed == 0) {
            runCatching { builder.addDisallowedApplication(vpn.packageName) }
            if (mode == 2) apps.forEach { runCatching { builder.addDisallowedApplication(it) } }
        }

        val established = builder.establish()
            ?: throw IllegalStateException("Android could not establish SSH VPN interface")
        tun = established
        return established.fd
    }

    private fun <T> emptyIterator(type: Class<T>): T {
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            when (method.name) {
                "hasNext" -> false
                "len" -> 0
                "toString" -> "empty iterator"
                else -> defaultValue(method.returnType)
            }
        } as T
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Void.TYPE -> null
        else -> null
    }

    @Synchronized
    fun stop() {
        val old = server
        server = null
        if (old != null) {
            runCatching { old.closeService() }
            runCatching { old.close() }
        }
        runCatching { tun?.close() }
        tun = null
    }
}
