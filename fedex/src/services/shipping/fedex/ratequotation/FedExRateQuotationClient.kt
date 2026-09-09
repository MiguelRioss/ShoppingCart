package services.shipping.fedex.ratequotation

import kotlinx.serialization.json.JsonObject

interface FedExRateQuotationClient {
    fun quote(
        request: FedExQuoteRequest
    ): JsonObject
}
