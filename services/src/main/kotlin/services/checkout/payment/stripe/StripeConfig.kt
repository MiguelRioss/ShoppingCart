
package services.checkout.payment.stripe

import config.Environment
import java.net.URI

/** Stripe credentials and endpoint configuration loaded by the payment adapter. */
data class StripeConfig(
    val secretKey: String,
    val checkoutApiUrl: String
) {

    companion object {

        /**
         * Loads Stripe credentials for a specific account.
         *
         * Supported accounts: PT and UK.
         */
        fun fromEnvironment(account: String): StripeConfig {

            require(account == "PT" || account == "UK") {
                "Unsupported Stripe account: $account"
            }

            val secretKey =
                Environment.get("STRIPE_${account}_SECRET_KEY")
                    ?: error(
                        "STRIPE_${account}_SECRET_KEY is required"
                    )

            val checkoutApiUrl = Environment.get("STRIPE_CHECKOUT_API_URL")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: error("STRIPE_CHECKOUT_API_URL is required")
            val checkoutUri = runCatching { URI(checkoutApiUrl) }.getOrNull()
            check(
                checkoutUri != null && checkoutUri.scheme in setOf("https", "http") &&
                    !checkoutUri.host.isNullOrBlank() && checkoutUri.rawQuery == null &&
                    checkoutUri.rawFragment == null && checkoutUri.userInfo == null
            ) { "STRIPE_CHECKOUT_API_URL must be an absolute HTTP(S) URL without query or credentials" }

            return StripeConfig(
                secretKey = secretKey,
                checkoutApiUrl = checkoutApiUrl
            )
        }

    }
}
