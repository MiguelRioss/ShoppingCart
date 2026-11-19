package domain.checkout.payment

/** Payment-provider identifiers exposed through provider-neutral checkout models. */
enum class PaymentProviderType(
    val responseValue: String
) {
    STRIPE("stripe")
}
