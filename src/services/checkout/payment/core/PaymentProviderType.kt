package services.checkout.payment.core

enum class PaymentProviderType(
    val responseValue: String
) {
    STRIPE("stripe")
}
