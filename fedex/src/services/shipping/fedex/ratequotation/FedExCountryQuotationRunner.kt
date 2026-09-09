package services.shipping.fedex.ratequotation

class FedExCountryQuotationRunner(
    private val rateClientFactory: () -> FedExRateQuotationClient,
    private val maxTransientAttempts: Int = 10,
    private val retryDelayMillis: Long = 1_500
) {

    fun quoteUntilResolved(
        destination: FedExCountryDestination,
        requestFactory: (
            FedExCountryDestination
        ) -> FedExQuoteRequest
    ): FedExCountryQuotationResult {

        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < maxTransientAttempts) {
            attempt++

            println()
            println(
                "FedEx ${destination.countryName} " +
                        "(${destination.countryCode}) " +
                        "attempt $attempt of $maxTransientAttempts"
            )

            try {
                val response =
                    rateClientFactory()
                        .quote(
                            requestFactory(destination)
                        )

                println(
                    "SUCCESS: ${destination.countryName}"
                )

                return FedExCountryQuotationResult(
                    countryName =
                        destination.countryName,
                    countryCode =
                        destination.countryCode,
                    city =
                        destination.city,
                    postalCode =
                        destination.postalCode,
                    success =
                        true,
                    attempts =
                        attempt,
                    response =
                        response.toString(),
                    error =
                        null
                )
            } catch (error: Throwable) {
                lastError = error

                val message =
                    error.message.orEmpty()

                println(
                    "FedEx error for " +
                            "${destination.countryName}: " +
                            message
                )

                if (!isTransientFedExFailure(message)) {
                    println(
                        "Permanent response. " +
                                "Moving to next country."
                    )

                    return FedExCountryQuotationResult(
                        countryName =
                            destination.countryName,
                        countryCode =
                            destination.countryCode,
                        city =
                            destination.city,
                        postalCode =
                            destination.postalCode,
                        success =
                            false,
                        attempts =
                            attempt,
                        response =
                            null,
                        error =
                            message
                    )
                }

                if (attempt < maxTransientAttempts) {
                    println(
                        "Temporary FedEx error. " +
                                "Retrying..."
                    )

                    Thread.sleep(
                        retryDelayMillis
                    )
                }
            }
        }

        return FedExCountryQuotationResult(
            countryName =
                destination.countryName,
            countryCode =
                destination.countryCode,
            city =
                destination.city,
            postalCode =
                destination.postalCode,
            success =
                false,
            attempts =
                attempt,
            response =
                null,
            error =
                lastError?.message
                    ?: "FedEx quotation attempts exhausted"
        )
    }

    private fun isTransientFedExFailure(
        message: String
    ): Boolean {
        val upper =
            message.uppercase()

        return upper.contains("STATUS 401") ||
                upper.contains("STATUS 408") ||
                upper.contains("STATUS 429") ||
                upper.contains("STATUS 500") ||
                upper.contains("STATUS 502") ||
                upper.contains("STATUS 503") ||
                upper.contains("STATUS 504") ||
                upper.contains("NOT.AUTHORIZED.ERROR") ||
                upper.contains("TIMEOUT") ||
                upper.contains("TIMED OUT") ||
                upper.contains("CONNECTION RESET") ||
                upper.contains("CONNECTION REFUSED")
    }
}