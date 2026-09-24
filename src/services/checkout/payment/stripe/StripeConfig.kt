package services.checkout.payment.stripe

import config.Environment

/** Stripe credentials and endpoint configuration loaded by the payment adapter. */
data class StripeConfig(
    val secretKey: String,
    val checkoutApiUrl: String
) {

    companion object {

        /** Loads the secret key and optional Checkout API override from the environment. */
        fun fromEnvironment(): StripeConfig {

            val secretKey =
                Environment.get("STRIPE_SECRET_KEY")
                    ?: error(
                        "STRIPE_SECRET_KEY is required"
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
