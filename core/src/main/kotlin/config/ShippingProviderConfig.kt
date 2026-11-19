package config

import shipment.core.ProviderAccount
import shipment.core.ProviderCredentials
import shipment.core.ShippingProviderEnvironment

/** Separate FedEx billing accounts for the Portuguese and UK shipment origins. */
data class ShippingProviderConfig(
    val mode: ShippingMode,
    val account: ProviderAccount?,
    val credentials: ProviderCredentials?,
    val providerEnvironment: ShippingProviderEnvironment?
) {
    companion object {
        fun load(accountCountry: String, read: (String) -> String? = { Environment.get(it) }): ShippingProviderConfig {
            val mode = mode(read)
            if (mode == ShippingMode.OFFLINE) return ShippingProviderConfig(mode, null, null, null)
            val region = when (accountCountry.trim().uppercase()) {
                "PT" -> "PT"
                "GB", "UK" -> "UK"
                else -> error("Unsupported FedEx billing account country")
            }
            val prefix = if (mode == ShippingMode.SANDBOX) "FEDEX_TEST_" else "FEDEX_PRODUCTION_"
            fun required(suffix: String): String {
                val name = prefix + region + "_" + suffix
                return read(name)?.takeIf(String::isNotBlank)?.trim()
                    ?: throw IllegalStateException("$name is required for the $region shipping account")
            }
            return ShippingProviderConfig(
                mode,
                ProviderAccount(required("ACCOUNT_NUMBER"), if (region == "UK") "GB" else "PT"),
                ProviderCredentials(required("CLIENT_ID"), required("CLIENT_SECRET")),
                if (mode == ShippingMode.SANDBOX) ShippingProviderEnvironment.SANDBOX else ShippingProviderEnvironment.PRODUCTION
            )
        }

        /** Reads the global integration mode without requiring any one region's account credentials. */
        fun mode(read: (String) -> String? = { Environment.get(it) }): ShippingMode =
            read("FEDEX_MODE")?.trim()?.uppercase()?.let(ShippingMode::valueOf) ?: ShippingMode.OFFLINE
    }
}

enum class ShippingMode { OFFLINE, SANDBOX, PRODUCTION }
