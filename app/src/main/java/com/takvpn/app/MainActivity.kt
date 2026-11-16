package com.takvpn.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var configInput: EditText
    private lateinit var connectButton: MaterialButton
    private var pendingConfig: String? = null

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConfig?.let { startVpn(it) }
        } else {
            toast("اجازه‌ی VPN داده نشد")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        configInput = findViewById(R.id.configInput)
        connectButton = findViewById(R.id.connectButton)
        val prefs = getSharedPreferences("takvpn", Context.MODE_PRIVATE)
        configInput.setText(prefs.getString("link", ""))

        findViewById<MaterialButton>(R.id.pasteButton).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) configInput.setText(text.trim()) else toast("کلیپ‌بورد خالیه")
        }

        connectButton.setOnClickListener {
            if (TakVpnService.isRunning) {
                startService(
                    Intent(this, TakVpnService::class.java).setAction(TakVpnService.ACTION_STOP)
                )
                return@setOnClickListener
            }
            val link = configInput.text.toString().trim()
            val json = try {
                ConfigParser.buildConfig(link)
            } catch (e: Exception) {
                toast("کانفیگ نامعتبر: ${e.message}")
                return@setOnClickListener
            }
            prefs.edit().putString("link", link).apply()
            pendingConfig = json
            val intent = VpnService.prepare(this)
            if (intent != null) vpnPermission.launch(intent) else startVpn(json)
        }
    }

    override fun onStart() {
        super.onStart()
        TakVpnService.onState = { running -> runOnUiThread { updateUi(running) } }
        TakVpnService.onError = { msg -> runOnUiThread { toast("خطا: $msg") } }
        updateUi(TakVpnService.isRunning)
    }

    override fun onStop() {
        super.onStop()
        TakVpnService.onState = null
        TakVpnService.onError = null
    }

    private fun startVpn(config: String) {
        startService(
            Intent(this, TakVpnService::class.java)
                .putExtra(TakVpnService.EXTRA_CONFIG, config)
        )
    }

    private fun updateUi(running: Boolean) {
        connectButton.text = if (running) "قطع اتصال" else "اتصال"
        statusText.text = if (running) "متصل" else "قطع"
        statusText.setTextColor(if (running) Color.parseColor("#FFC107") else Color.parseColor("#BBBBBB"))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
