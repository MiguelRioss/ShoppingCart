import services.checkout.payment.core.PaymentProviderInterface

class PaymentProviderResolver(
    private val portugueseStripe: PaymentProviderInterface,
    private val britishStripe: PaymentProviderInterface
) {

    fun resolve(
        originCountry: String,
        destinationCountry: String
    ): PaymentProviderInterface {

        return when {
            originCountry == "PT" && destinationCountry == "GB" ->
                britishStripe

            originCountry == "PT" ->
                portugueseStripe

            else ->
                throw IllegalArgumentException(
                    "Unsupported shipping route"
                )
        }
    }
}