package services.shipping.fedex

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize

data class FedExMappedShipment(
    val originAddress: FedExAddress,
    val destinationAddress: FedExAddress,
    val packageData: FedExPackageData
)

data class FedExAddress(
    val streetLines: List<String>,
    val city: String,
    val postalCode: String,
    val countryCode: String
)

data class FedExPackageData(
    val length: Int,
    val width: Int,
    val height: Int,
    val weightKg: Double
)

interface FedExShipmentMapperInterface {
    fun map(
        originAddress: ShippingAddress,
        destinationAddress: ShippingAddress,
        packageSize: ShippingPackageSize
    ): FedExMappedShipment
}