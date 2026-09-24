package services.checkout.payment.core

/** Payment-provider identifiers exposed through provider-neutral checkout models. */
enum class PaymentProviderType(
    val responseValue: String
) {
    STRIPE("stripe")
}
