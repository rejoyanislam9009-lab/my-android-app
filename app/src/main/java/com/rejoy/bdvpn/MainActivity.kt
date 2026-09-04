package com.rejoy.bdvpn

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val VPN_PERMISSION_REQUEST = 4101
    }

    private val serverProfile = ServerProfiles.BANGLADESH_PRIMARY
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
        val smallGap = (10 * density).toInt()
        val cardRadius = 24 * density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(244, 248, 246))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            background = roundedBackground(Color.rgb(8, 107, 76), cardRadius)
            elevation = 8 * density
        }
        header.addView(TextView(this).apply {
            text = "Bangla VPN"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        header.addView(TextView(this).apply {
            text = "Secure Bangladesh WireGuard tunnel"
            textSize = 15f
            setTextColor(Color.rgb(226, 245, 237))
            gravity = Gravity.CENTER
            setPadding(0, smallGap / 2, 0, 0)
        })
        container.addView(header)

        val serverCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = roundedBackground(Color.WHITE, cardRadius)
            elevation = 4 * density
        }
        (serverCard.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = smallGap
        serverCard.addView(TextView(this).apply {
            text = "🇧🇩 ${serverProfile.name}"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(20, 53, 44))
        })
        serverCard.addView(TextView(this).apply {
            text = "Authorized Bangladesh-hosted server profile"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, smallGap / 2, 0, smallGap)
        })

        privateKey = field("Client private key", "", true)
        address = field("Client address (CIDR)", "10.8.0.2/32")
        dns = field("DNS", serverProfile.dns)
        peerPublicKey = field("Bangladesh server public key", "")
        endpoint = field("Bangladesh server endpoint host:port", serverProfile.endpoint)
        allowedIps = field("Allowed IPs", serverProfile.allowedIps)

        listOf(privateKey, address, dns, peerPublicKey, endpoint, allowedIps).forEach {
            serverCard.addView(it)
        }

        container.addView(serverCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = smallGap })

        val connect = actionButton("CONNECT", Color.rgb(8, 107, 76)).apply {
            setOnClickListener { requestConnect() }
        }
        val disconnect = actionButton("DISCONNECT", Color.rgb(84, 91, 88)).apply {
            setOnClickListener { disconnectTunnel() }
        }

        container.addView(connect, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = smallGap * 2 })
        container.addView(disconnect, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = smallGap })

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(20, 53, 44))
            setPadding(padding, padding, padding, padding)
            background = roundedBackground(Color.WHITE, cardRadius)
        }
        container.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = smallGap })

        container.addView(TextView(this).apply {
            text = "Use only a server you own or are authorized to use. This app does not override a financial service's KYC, fraud-prevention, or access policies."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(4, smallGap, 4, 0)
        })

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

    private fun field(hintText: String, value: String, password: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            setSingleLine(true)
            setTextColor(Color.rgb(28, 42, 37))
            setHintTextColor(Color.rgb(120, 128, 124))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(8, 107, 76))
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }
    }

    private fun actionButton(label: String, color: Int): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            background = roundedBackground(color, 18 * density)
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
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
        status.text = "Connecting to ${serverProfile.name}..."

        thread {
            try {
                val newState = controller.connect(config)
                runOnUiThread {
                    status.text = "${serverProfile.name}: ${newState.name}"
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
        val peerPublicKeyValue = required(peerPublicKey, "Bangladesh server public key")
        val endpointValue = required(endpoint, "Bangladesh server endpoint")
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
            appendLine("PersistentKeepalive = ${serverProfile.persistentKeepaliveSeconds}")
        }
    }

    private fun required(field: EditText, label: String): String {
        return field.text.toString().trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$label is required")
    }
}
