package com.rejoy.bdvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.wireguard.android.backend.Tunnel
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val VPN_PERMISSION_REQUEST = 4101
    }

    private lateinit var controller: VpnTunnelController
    private lateinit var privateKey: EditText
    private lateinit var address: EditText
    private lateinit var dns: EditText
    private lateinit var peerPublicKey: EditText
    private lateinit var endpoint: EditText
    private lateinit var allowedIps: EditText
    private lateinit var status: TextView

    private var pendingConfig: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = VpnTunnelController(this)
        setContentView(buildUi())
        refreshStatus()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        container.addView(TextView(this).apply {
            text = "Bangla VPN MVP"
            textSize = 26f
            gravity = Gravity.CENTER_HORIZONTAL
        })

        container.addView(TextView(this).apply {
            text = "WireGuard client for an authorized VPN server. Keys are not saved by this MVP."
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
        })

        privateKey = field(
            hintText = "Client private key",
            value = "",
            password = true
        )
        address = field("Client address (CIDR)", "10.8.0.2/32")
        dns = field("DNS", "1.1.1.1")
        peerPublicKey = field("Server public key", "")
        endpoint = field("Server endpoint host:port", "vpn.example.com:51820")
        allowedIps = field("Allowed IPs", "0.0.0.0/0, ::/0")

        listOf(privateKey, address, dns, peerPublicKey, endpoint, allowedIps).forEach {
            container.addView(it)
        }

        val connect = Button(this).apply {
            text = "Connect"
            setOnClickListener { requestConnect() }
        }

        val disconnect = Button(this).apply {
            text = "Disconnect"
            setOnClickListener { disconnectTunnel() }
        }

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, (16 * density).toInt(), 0, 0)
        }

        container.addView(connect)
        container.addView(disconnect)
        container.addView(status)

        return ScrollView(this).apply {
            addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun field(
        hintText: String,
        value: String,
        password: Boolean = false
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            setSingleLine(true)
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }
    }

    private fun requestConnect() {
        val config = try {
            buildWireGuardConfig()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message ?: "Invalid configuration", Toast.LENGTH_LONG).show()
            return
        }

        pendingConfig = config
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST)
        } else {
            connectPendingConfig()
        }
    }

    @Deprecated("Deprecated in Android API; retained for a minimal Activity-only MVP")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) {
                connectPendingConfig()
            } else {
                pendingConfig = null
                status.text = "VPN permission was not granted."
            }
        }
    }

    private fun connectPendingConfig() {
        val config = pendingConfig ?: return
        status.text = "Connecting..."

        thread {
            try {
                val newState = controller.connect(config)
                runOnUiThread {
                    status.text = "VPN state: ${newState.name}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Connection failed: ${e.message ?: e.javaClass.simpleName}"
                }
            } finally {
                pendingConfig = null
            }
        }
    }

    private fun disconnectTunnel() {
        status.text = "Disconnecting..."
        thread {
            try {
                val newState = controller.disconnect()
                runOnUiThread {
                    status.text = "VPN state: ${newState.name}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Disconnect failed: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun refreshStatus() {
        thread {
            val state = controller.currentState()
            runOnUiThread {
                status.text = "VPN state: ${state.name}"
            }
        }
    }

    private fun buildWireGuardConfig(): String {
        val privateKeyValue = required(privateKey, "Client private key")
        val addressValue = required(address, "Client address")
        val peerPublicKeyValue = required(peerPublicKey, "Server public key")
        val endpointValue = required(endpoint, "Server endpoint")
        val allowedIpsValue = required(allowedIps, "Allowed IPs")
        val dnsValue = dns.text.toString().trim()

        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKeyValue")
            appendLine("Address = $addressValue")
            if (dnsValue.isNotEmpty()) {
                appendLine("DNS = $dnsValue")
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $peerPublicKeyValue")
            appendLine("Endpoint = $endpointValue")
            appendLine("AllowedIPs = $allowedIpsValue")
            appendLine("PersistentKeepalive = 25")
        }
    }

    private fun required(field: EditText, label: String): String {
        return field.text.toString().trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$label is required")
    }
}
