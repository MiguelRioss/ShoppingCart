package config

import shipment.core.ProviderAccount
import shipment.core.ProviderCredentials
import shipment.core.ShippingProviderEnvironment

/** Shipping configuration owned and loaded by the ShoppingCart application. */
data class ShippingProviderConfig(
    val mode: ShippingMode,
    val account: ProviderAccount?,
    val credentials: ProviderCredentials?,
    val providerEnvironment: ShippingProviderEnvironment?
) {
    companion object {
        fun load(): ShippingProviderConfig {
            val mode =
                Environment.get("FEDEX_MODE")
                    ?.uppercase()
                    ?.let(ShippingMode::valueOf)
                    ?: ShippingMode.OFFLINE

            if (mode == ShippingMode.OFFLINE) {
                return ShippingProviderConfig(mode, null, null, null)
            }

            val prefix =
                when (mode) {
                    ShippingMode.SANDBOX -> "FEDEX_TEST_"
                    ShippingMode.PRODUCTION -> "FEDEX_PRODUCTION_"
                    ShippingMode.OFFLINE -> error("Offline mode has no credentials")
                }

            return ShippingProviderConfig(
                mode = mode,
                account =
                    ProviderAccount(
                        accountNumber = required(prefix + "ACCOUNT_NUMBER"),
                        countryCode = required(prefix + "ACCOUNT_COUNTRY_CODE")
                    ),
                credentials =
                    ProviderCredentials(
                        clientId = required(prefix + "CLIENT_ID"),
                        clientSecret = required(prefix + "CLIENT_SECRET")
                    ),
                providerEnvironment =
                    when (mode) {
                        ShippingMode.SANDBOX -> ShippingProviderEnvironment.SANDBOX
                        ShippingMode.PRODUCTION -> ShippingProviderEnvironment.PRODUCTION
                        ShippingMode.OFFLINE -> error("Offline mode has no endpoint")
                    }
            )
        }

        private fun required(name: String): String =
            requireNotNull(Environment.get(name)?.takeIf(String::isNotBlank)) {
                "$name is required"
            }
    }
}

enum class ShippingMode {
    OFFLINE,
    SANDBOX,
    PRODUCTION
}
