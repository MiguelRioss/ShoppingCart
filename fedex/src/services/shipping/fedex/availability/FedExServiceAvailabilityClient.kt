package services.shipping.fedex.availability

import domain.shipping.ShippingAddress
import services.shipping.fedex.ratequotation.FedExCustomsDetail

interface FedExServiceAvailabilityClient {
    fun packageAndServiceOptions(
        request: FedExServiceAvailabilityRequest
    ): FedExServiceAvailabilityResponse
}

data class FedExServiceAvailabilityRequest(
    val originAddress: ShippingAddress,
    val destinationAddress: ShippingAddress,
    val customsDetail: FedExCustomsDetail? = null
)

data class FedExServiceAvailabilityResponse(
    val options: List<FedExServicePackageOption>,
    val rawResponse: String
)

data class FedExServicePackageOption(
    val serviceType: String,
    val packagingType: String
)
