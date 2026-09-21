package services.shipping

import shipment.core.ShipmentAddress
import shipment.core.ShipmentPackage

data class ShipmentShippingRequest(
    val from: ShipmentAddress,
    val products: List<ShipmentPackage>
)
