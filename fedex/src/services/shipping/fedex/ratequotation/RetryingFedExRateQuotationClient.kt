package services.shipping.fedex.ratequotation

import kotlinx.serialization.json.JsonObject

class RetryingFedExRateQuotationClient(
    private val delegate: FedExRateQuotationClient,
    private val maxAttempts: Int? = 5,
    private val delayMillis: (attempt: Int) -> Long = ::defaultDelayMillis,
    private val sleep: (Long) -> Unit = Thread::sleep
) : FedExRateQuotationClient {

    init {
        require(maxAttempts == null || maxAttempts > 0) {
            "maxAttempts must be greater than zero"
        }
    }

    override fun quote(
        request: FedExQuoteRequest
    ): JsonObject {
        var lastException: RuntimeException? = null
        var attempt = 1

        while (maxAttempts == null || attempt <= maxAttempts) {
            try {
                println(
                    "FedEx rate quotation attempt $attempt" +
                            (maxAttempts?.let { " of $it" } ?: "")
                )
                return delegate.quote(request)
            } catch (exception: RuntimeException) {
                lastException = exception

                if (attempt == maxAttempts) {
                    break
                }

                val delay = delayMillis(attempt)
                println(
                    "FedEx rate quotation attempt $attempt failed; " +
                            "retrying in ${delay}ms"
                )
                sleep(delay)
            }

            attempt++
        }

        throw IllegalStateException(
            "FedEx rate quotation did not return 200 OK after $maxAttempts attempts",
            lastException
        )
    }

    private companion object {
        fun defaultDelayMillis(
            attempt: Int
        ): Long =
            when (attempt) {
                1 -> 1_000L
                2 -> 2_000L
                3 -> 4_000L
                else -> 8_000L
            }
    }
}
