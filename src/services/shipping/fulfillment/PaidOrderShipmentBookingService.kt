package services.shipping.fulfillment

import ShoppingCartProduct
import config.Environment
import db.OrderRepository
import domain.cart.ShoppingCart
import domain.checkout.CheckoutDeliveryAddress
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentContact
import domain.shipment.ShipmentRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.*
import shipment.core.ShippingProvider
import services.shipping.ProductShipmentRequestMapper

/** Books FedEx shipments only for paid checkouts, and only in explicit sandbox mode. */
class PaidOrderShipmentBookingService(
    private val orders: OrderRepository,
    private val requestMapper: ProductShipmentRequestMapper,
    private val carrier: ShippingProvider,
    private val mode: () -> String? = { Environment.get("FEDEX_MODE") }
) {
    fun bookPaidOrder(checkoutId: String): Boolean {
        if (mode()?.trim()?.uppercase() != "SANDBOX") return false
        val order = orders.findByCheckoutForEmail(checkoutId)
            ?: throw IllegalArgumentException("Order not found for checkout $checkoutId")
        require(order.text("status") == "PAID") { "FedEx shipment booking requires paid order" }
        val snapshot = order.obj("snapshot")
        val cart = snapshot.toCart()
        val destination = snapshot.obj("deliveryAddress")
        val to = CheckoutDeliveryAddress(
            company = destination.text("company"),
            addressLine1 = destination.text("addressLine1"),
            addressLine2 = destination.text("addressLine2"),
            townOrCity = destination.text("townOrCity"),
            postcode = destination.text("postcode"),
            country = destination.text("country")
        ).toShipmentAddress()
        val customerName = listOfNotNull(snapshot.text("customerFirstName"), snapshot.text("customerLastName"))
            .joinToString(" ").ifBlank { destination.text("company") ?: "Customer" }
        val phone = snapshot.text("customerPhone")
            ?: throw IllegalArgumentException("Customer phone is required for FedEx shipment booking")
        val shipmentRequests = try {
            requestMapper.toShipmentShippingRequests(cart)
        } catch (error: Exception) {
            recordPreflightFailure(checkoutId, error)
            return false
        }
        val serviceTypes = (snapshot.obj("shipping")["originServiceTypes"] as? JsonArray)
            ?.map { it.jsonPrimitive.content }.orEmpty()
        if (serviceTypes.size != shipmentRequests.size) {
            recordPreflightFailure(checkoutId,
                IllegalArgumentException("Checkout snapshot is missing the selected FedEx service for each origin"))
            return false
        }
        shipmentRequests.forEachIndexed { index, request ->
            val originKey = "origin-${index + 1}"
            if (!orders.claimShipmentBooking(checkoutId, originKey)) return@forEachIndexed
            try {
                val shipment = carrier.prepareShipping(
                    ShipmentRequest(
                        from = request.from,
                        shipperContact = request.shipperContact
                            ?: throw IllegalArgumentException("Supplier phone/contact is required for FedEx shipment booking"),
                        to = to,
                        recipientContact = ShipmentContact(
                            personName = customerName,
                            phoneNumber = phone,
                            companyName = destination.text("company")
                        ),
                        packages = request.products,
                        serviceType = serviceTypes[index]
                    )
                )
                val booking = buildJsonObject {
                    put("provider", shipment.provider.name)
                    put("shipmentId", shipment.shipmentId)
                    put("masterTrackingNumber", shipment.masterTrackingNumber)
                    put("serviceType", shipment.serviceType)
                    put("shipDate", shipment.shipDate)
                    put("packages", JsonArray(shipment.packages.map { pkg -> buildJsonObject {
                        put("sequenceNumber", pkg.sequenceNumber)
                        put("trackingNumber", pkg.trackingNumber)
                    } }))
                    put("documents", JsonArray(shipment.documents.map { document -> buildJsonObject {
                        put("type", document.type.name)
                        put("format", document.format)
                        document.url?.let { put("url", it) }
                        document.content?.takeIf { it.isNotEmpty() }?.let { bytes ->
                            put("contentBase64", Base64.getEncoder().encodeToString(bytes))
                        }
                    } }))
                    put("alerts", JsonArray(shipment.alerts.map { JsonPrimitive(it.toString()) }))
                }
                orders.completeShipmentBooking(checkoutId, originKey, booking)
            } catch (error: Exception) {
                // A timeout can happen after FedEx creates the shipment. Never auto-book a second label.
                orders.failShipmentBooking(checkoutId, originKey,
                    "${error.javaClass.simpleName}: ${error.message ?: "FedEx booking outcome requires review"}")
                System.err.println("FedEx shipment booking needs review for $checkoutId $originKey: ${error.message}")
            }
        }
        val bookings = orders.shipmentBookings(checkoutId).mapNotNull { it as? JsonObject }
        return bookings.size == shipmentRequests.size && bookings.all { booking ->
            val details = booking["booking"] as? JsonObject ?: booking
            booking.text("status") == "BOOKED" && !details.text("masterTrackingNumber").isNullOrBlank()
        }
    }

    private fun recordPreflightFailure(checkoutId: String, error: Exception) {
        if (orders.claimShipmentBooking(checkoutId, "preflight")) {
            orders.failShipmentBooking(checkoutId, "preflight",
                "${error.javaClass.simpleName}: ${error.message ?: "Shipment data requires review"}")
        }
        System.err.println("FedEx shipment preflight needs review for $checkoutId: ${error.message}")
    }

    private fun JsonObject.toCart(): ShoppingCart {
        val entries = (this["cartProducts"] as? JsonArray)?.map { it.jsonObject }
            ?: throw IllegalArgumentException("Order snapshot cart products are missing")
        val products = entries.map { line ->
            ShoppingCartProduct(
                productId = line.requiredText("productId").toLong(),
                squareMeters = line.text("squareMeters")?.toDouble(),
                amountBoxes = line.text("amountBoxes")?.toInt(),
                totalPricePerProduct = line.requiredText("totalPricePerProduct").toBigDecimal(),
                isSample = line.text("isSample")?.toBooleanStrictOrNull() ?: false,
                sampleUnits = line.text("sampleUnits")?.toInt()
            )
        }
        return ShoppingCart(
            id = UUID.fromString(requiredText("cartId")),
            userId = text("userId")?.let(UUID::fromString),
            dateTime = LocalDateTime.parse(requiredText("cartUpdatedAt")),
            products = products
        )
    }

    private fun JsonObject.obj(field: String): JsonObject =
        this[field] as? JsonObject ?: throw IllegalArgumentException("Order snapshot $field is missing")
    private fun JsonObject.text(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    private fun JsonObject.requiredText(field: String): String =
        text(field) ?: throw IllegalArgumentException("Order snapshot $field is missing")
}
