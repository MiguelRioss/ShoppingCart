package domain.checkout

import shipment.core.ShipmentAddress

fun CreateCheckoutSessionRequest.toShipmentDestination(): ShipmentAddress =
    requireNotNull(deliveryAddress) {
        "deliveryAddress is required when shippingCharge is not supplied"
    }.toShipmentAddress()
