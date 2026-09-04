package com.rejoy.bdvpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

class VpnTunnelController(context: Context) {
    private val backend: Backend = GoBackend(context.applicationContext)

    @Volatile
    private var lastKnownState: Tunnel.State = Tunnel.State.DOWN

    private val tunnel = object : Tunnel {
        override fun getName(): String = "bd-vpn"

        override fun onStateChange(newState: Tunnel.State) {
            lastKnownState = newState
        }
    }

    fun connect(wgQuickConfig: String): Tunnel.State {
        val config = BufferedReader(StringReader(wgQuickConfig)).use { reader ->
            Config.parse(reader)
        }
        lastKnownState = backend.setState(tunnel, Tunnel.State.UP, config)
        return lastKnownState
    }

    fun disconnect(): Tunnel.State {
        lastKnownState = backend.setState(tunnel, Tunnel.State.DOWN, null)
        return lastKnownState
    }

    fun currentState(): Tunnel.State {
        return try {
            backend.getState(tunnel).also { lastKnownState = it }
        } catch (_: Exception) {
            lastKnownState
        }
    }
}
