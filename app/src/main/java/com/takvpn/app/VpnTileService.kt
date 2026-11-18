package com.takvpn.app

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class VpnTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (TakVpnService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Takvpn"
        t.updateTile()
    }

    override fun onClick() {
        if (TakVpnService.isRunning) {
            startService(Intent(this, TakVpnService::class.java).setAction(TakVpnService.ACTION_STOP))
            return
        }
        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }
        val store = ConfigStore(this)
        if (store.items.isEmpty()) {
            openApp()
            return
        }
        val prefs = getSharedPreferences("takvpn", MODE_PRIVATE)
        val i = Intent(this, TakVpnService::class.java)
        if (prefs.getBoolean("smart_sel", false)) {
            i.action = TakVpnService.ACTION_SMART
        } else {
            val cfg = store.selected()
            if (cfg == null) {
                openApp()
                return
            }
            val json = try {
                ConfigExtras.build(cfg.link)
            } catch (e: Throwable) {
                openApp()
                return
            }
            i.putExtra(TakVpnService.EXTRA_CONFIG, json)
                .putExtra(TakVpnService.EXTRA_SMART, false)
                .putExtra(TakVpnService.EXTRA_NAME, cfg.name)
                .putExtra(TakVpnService.EXTRA_LINK, cfg.link)
        }
        try {
            ContextCompat.startForegroundService(this, i)
        } catch (e: Throwable) {
            openApp()
        }
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        } catch (e: Throwable) {
        }
    }
}
