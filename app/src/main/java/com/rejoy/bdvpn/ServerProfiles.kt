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
     * IPv4 full-tunnel is the safe default for the bootstrap server in this repo;
     * add IPv6 only after the server is configured to route it correctly.
     */
    val BANGLADESH_PRIMARY = VpnServerProfile(
        name = "Bangladesh Primary",
        countryCode = "BD",
        endpoint = "",
        dns = "1.1.1.1",
        allowedIps = "0.0.0.0/0",
        persistentKeepaliveSeconds = 25
    )
}
