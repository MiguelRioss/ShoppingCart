package domain.checkout

import domain.shipment.ShipmentAddress

/**
 * Resolves checkout delivery information into a carrier-neutral destination.
 * @throws IllegalArgumentException when delivery information is absent or invalid
 */
fun CreateCheckoutSessionRequest.toShipmentDestination(): ShipmentAddress =
    requireNotNull(deliveryAddress) {
        "deliveryAddress is required when shippingCharge is not supplied"
    }.toShipmentAddress()
