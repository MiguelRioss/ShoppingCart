package services.shipping.fedex.ratequotation

import domain.shipping.Incoterm
import domain.shipping.ShippingAddress
import domain.shipping.ShippingOption
import domain.shipping.ShippingPackageSize
import java.time.LocalDate

interface FedExQuoteClient {
    fun quote(
        request: FedExQuoteRequest
    ): List<ShippingOption>
}

data class FedExQuoteRequest(
    val originAddress: ShippingAddress,
    val destinationAddress: ShippingAddress,
    val packageSize: ShippingPackageSize,
    val incoterm: Incoterm?,
    val customsDetail: FedExCustomsDetail? = null,
    val rateRequestTypes: List<String> = listOf("LIST"),
    val serviceType: String? = null,
    val packagingType: String? = null,
    val pickupType: String = "USE_SCHEDULED_PICKUP",
    val shipDate: LocalDate? = null,
    val includeDimensions: Boolean = true,
    val returnTransitTimes: Boolean = true,
    val postalCodeOnlyAddresses: Boolean = false
)
