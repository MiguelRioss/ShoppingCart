package services.shipping

import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentPackage
import domain.shipment.ShipmentContact

/**
 * One carrier rate request derived from cart lines sharing supplier origin [from].
 * A checkout may contain several instances when products have different origins.
 */
data class ShipmentShippingRequest(
    val from: ShipmentAddress,
    val shipperContact: ShipmentContact?,
    val products: List<ShipmentPackage>
)
