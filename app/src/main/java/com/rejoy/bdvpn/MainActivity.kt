package com.rejoy.bdvpn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        private const val VPN_PERMISSION_REQUEST = 4101
    }

    private lateinit var controller: VpnTunnelController
    private lateinit var serverSpinner: Spinner
    private lateinit var status: TextView

    private var pendingConfig: String? = null
    private var pendingProfile: VpnServerProfile? = null

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
            text = "Select a Bangladesh server and connect"
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
        serverCard.addView(TextView(this).apply {
            text = "🇧🇩 Choose server"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(20, 53, 44))
        })
        serverCard.addView(TextView(this).apply {
            text = "Your server credentials are preconfigured in the build. No manual key or endpoint entry is required."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, smallGap / 2, 0, smallGap)
        })

        serverSpinner = Spinner(this).apply {
            val labels = ServerProfiles.all.map { it.displayName }
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                labels
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setPadding(smallGap, smallGap, smallGap, smallGap)
            background = roundedBackground(Color.rgb(235, 244, 240), 14 * density)
        }
        serverCard.addView(
            serverSpinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            serverCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = smallGap }
        )

        val connect = actionButton("CONNECT", Color.rgb(8, 107, 76)).apply {
            setOnClickListener { requestConnect() }
        }
        val disconnect = actionButton("DISCONNECT", Color.rgb(84, 91, 88)).apply {
            setOnClickListener { disconnectTunnel() }
        }

        container.addView(
            connect,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = smallGap * 2 }
        )
        container.addView(
            disconnect,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = smallGap }
        )

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(20, 53, 44))
            setPadding(padding, padding, padding, padding)
            background = roundedBackground(Color.WHITE, cardRadius)
        }
        container.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = smallGap }
        )

        container.addView(TextView(this).apply {
            text = "Use only servers you own or are authorized to use. Availability of third-party financial services still depends on their own KYC, anti-fraud, VPN, and access policies."
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

    private fun selectedProfile(): VpnServerProfile {
        val index = serverSpinner.selectedItemPosition.coerceIn(0, ServerProfiles.all.lastIndex)
        return ServerProfiles.all[index]
    }

    private fun requestConnect() {
        val profile = selectedProfile()
        if (!profile.isConfigured) {
            status.text = "${profile.name}: server setup pending"
            Toast.makeText(
                this,
                "${profile.name} is not provisioned in this build yet.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        pendingProfile = profile
        pendingConfig = buildWireGuardConfig(profile)

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
                pendingProfile = null
                status.text = "VPN permission was not granted."
            }
        }
    }

    private fun connectPendingConfig() {
        val config = pendingConfig ?: return
        val profile = pendingProfile ?: selectedProfile()
        status.text = "Connecting to ${profile.name}..."

        thread {
            try {
                val newState = controller.connect(config)
                runOnUiThread {
                    status.text = "${profile.name}: ${newState.name}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Connection failed: ${e.message ?: e.javaClass.simpleName}"
                }
            } finally {
                pendingConfig = null
                pendingProfile = null
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

    private fun buildWireGuardConfig(profile: VpnServerProfile): String {
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${profile.clientPrivateKey}")
            appendLine("Address = ${profile.clientAddress}")
            if (profile.dns.isNotBlank()) {
                appendLine("DNS = ${profile.dns}")
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${profile.serverPublicKey}")
            appendLine("Endpoint = ${profile.endpoint}")
            appendLine("AllowedIPs = ${profile.allowedIps}")
            appendLine("PersistentKeepalive = ${profile.persistentKeepaliveSeconds}")
        }
    }
}
