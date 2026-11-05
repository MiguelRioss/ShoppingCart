
package services.checkout.payment.stripe

import config.Environment

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

            val checkoutApiUrl =
                Environment.get("STRIPE_CHECKOUT_API_URL")
                    ?: DEFAULT_CHECKOUT_API_URL

            return StripeConfig(
                secretKey = secretKey,
                checkoutApiUrl = checkoutApiUrl
            )
        }

        private const val DEFAULT_CHECKOUT_API_URL =
            "https://api.stripe.com/v1/checkout/sessions"
    }
}
