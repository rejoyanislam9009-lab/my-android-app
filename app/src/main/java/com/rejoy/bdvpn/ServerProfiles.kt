package com.rejoy.bdvpn

data class VpnServerProfile(
    val name: String,
    val countryCode: String,
    val endpoint: String,
    val serverPublicKey: String,
    val clientPrivateKey: String,
    val clientAddress: String,
    val dns: String = "1.1.1.1",
    val allowedIps: String = "0.0.0.0/0",
    val persistentKeepaliveSeconds: Int = 25
) {
    val isConfigured: Boolean
        get() = endpoint.isNotBlank() &&
            serverPublicKey.isNotBlank() &&
            clientPrivateKey.isNotBlank() &&
            clientAddress.isNotBlank()

    val displayName: String
        get() = "🇧🇩 $name${if (isConfigured) " • Ready" else " • Setup pending"}"
}

object ServerProfiles {
    private fun nameOrDefault(configuredName: String, fallback: String): String =
        configuredName.trim().ifEmpty { fallback }

    val BANGLADESH_1 = VpnServerProfile(
        name = nameOrDefault(BuildConfig.BD_VPN_1_NAME, "Bangladesh 1"),
        countryCode = "BD",
        endpoint = BuildConfig.BD_VPN_1_ENDPOINT.trim(),
        serverPublicKey = BuildConfig.BD_VPN_1_SERVER_PUBLIC_KEY.trim(),
        clientPrivateKey = BuildConfig.BD_VPN_1_CLIENT_PRIVATE_KEY.trim(),
        clientAddress = BuildConfig.BD_VPN_1_CLIENT_ADDRESS.trim()
    )

    val BANGLADESH_2 = VpnServerProfile(
        name = nameOrDefault(BuildConfig.BD_VPN_2_NAME, "Bangladesh 2"),
        countryCode = "BD",
        endpoint = BuildConfig.BD_VPN_2_ENDPOINT.trim(),
        serverPublicKey = BuildConfig.BD_VPN_2_SERVER_PUBLIC_KEY.trim(),
        clientPrivateKey = BuildConfig.BD_VPN_2_CLIENT_PRIVATE_KEY.trim(),
        clientAddress = BuildConfig.BD_VPN_2_CLIENT_ADDRESS.trim()
    )

    val BANGLADESH_3 = VpnServerProfile(
        name = nameOrDefault(BuildConfig.BD_VPN_3_NAME, "Bangladesh 3"),
        countryCode = "BD",
        endpoint = BuildConfig.BD_VPN_3_ENDPOINT.trim(),
        serverPublicKey = BuildConfig.BD_VPN_3_SERVER_PUBLIC_KEY.trim(),
        clientPrivateKey = BuildConfig.BD_VPN_3_CLIENT_PRIVATE_KEY.trim(),
        clientAddress = BuildConfig.BD_VPN_3_CLIENT_ADDRESS.trim()
    )

    val all: List<VpnServerProfile> = listOf(BANGLADESH_1, BANGLADESH_2, BANGLADESH_3)
}
