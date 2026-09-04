package com.rejoy.bdvpn

data class VpnServerProfile(
    val name: String,
    val countryCode: String,
    val endpoint: String,
    val dns: String,
    val allowedIps: String,
    val persistentKeepaliveSeconds: Int
)

object ServerProfiles {
    /**
     * Production slot for an authorized Bangladesh-hosted WireGuard server.
     *
     * Endpoint and keys are intentionally not committed to source control.
     * Provision a real Bangladesh VPS, then supply its endpoint/public key at runtime.
     */
    val BANGLADESH_PRIMARY = VpnServerProfile(
        name = "Bangladesh Primary",
        countryCode = "BD",
        endpoint = "",
        dns = "1.1.1.1",
        allowedIps = "0.0.0.0/0, ::/0",
        persistentKeepaliveSeconds = 25
    )
}
