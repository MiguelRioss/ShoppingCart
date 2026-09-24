package services.shipping

import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentPackage

/**
 * One carrier rate request derived from cart lines sharing supplier origin [from].
 * A checkout may contain several instances when products have different origins.
 */
data class ShipmentShippingRequest(
    val from: ShipmentAddress,
    val products: List<ShipmentPackage>
)
