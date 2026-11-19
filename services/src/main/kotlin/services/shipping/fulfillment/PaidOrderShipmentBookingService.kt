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
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.serialization.json.*
import shipment.core.ShippingProvider
import services.shipping.AccountRoutingShippingProvider
import services.shipping.ProductShipmentRequestMapper

/** Books FedEx shipments for paid orders in sandbox or production with durable duplicate protection. */
class PaidOrderShipmentBookingService(
    private val orders: OrderRepository,
    private val requestMapper: ProductShipmentRequestMapper,
    private val carrier: ShippingProvider,
    private val mode: () -> String? = { Environment.get("FEDEX_MODE") }
) {
    private val logger = Logger.getLogger(javaClass.name)

    fun bookPaidOrder(orderId: String, shippingDate: LocalDate): Boolean {
        check(mode()?.trim()?.uppercase() in setOf("SANDBOX", "PRODUCTION")) {
            "FedEx shipment booking requires FEDEX_MODE=SANDBOX or PRODUCTION"
        }
        val order = orders.findForEmail(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val checkoutId = order.requiredText("checkoutSessionId")
        return bookPaidCheckout(checkoutId, order, shippingDate)
    }

    /** Legacy checkout entry point retained for existing callers and tests. */
    fun bookPaidOrder(checkoutId: String): Boolean {
        if (mode()?.trim()?.uppercase() !in setOf("SANDBOX", "PRODUCTION")) return false
        val order = orders.findByCheckoutForEmail(checkoutId)
            ?: throw IllegalArgumentException("Order not found for checkout $checkoutId")
        return bookPaidCheckout(checkoutId, order, LocalDate.now())
    }

    private fun bookPaidCheckout(checkoutId: String, order: JsonObject, shippingDate: LocalDate): Boolean {
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
            if (!orders.claimShipmentBooking(checkoutId, originKey)) {
                val prior = orders.shipmentBookings(checkoutId).mapNotNull { it as? JsonObject }
                    .firstOrNull { it.text("originKey") == originKey }
                val priorBooking = prior?.get("booking") as? JsonObject
                check(prior?.text("status") == "BOOKED" && priorBooking?.text("requestedShippingDate") == shippingDate.toString()) {
                    "Shipment booking $originKey already exists with a different date or requires manual review"
                }
                return@forEachIndexed
            }
            val shipmentRequest = try {
                
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
            } catch (error: Exception) {
                val diagnostic = "  request preparation error: ${error.javaClass.simpleName}: ${error.message ?: "invalid shipment request"}"
                orders.failShipmentBooking(checkoutId, originKey, diagnostic)
                logger.log(Level.SEVERE, "FedEx shipment request preparation failed:\n  checkoutId: $checkoutId\n  originKey: $originKey\n$diagnostic", error)
                return@forEachIndexed
            }
            try {
                val shipment = (carrier as? AccountRoutingShippingProvider)?.prepareShipping(shipmentRequest, shippingDate)
                    ?: carrier.prepareShipping(shipmentRequest)
                val booking = buildJsonObject {
                    put("provider", shipment.provider.name)
                    put("shipmentId", shipment.shipmentId)
                    put("masterTrackingNumber", shipment.masterTrackingNumber)
                    put("serviceType", shipment.serviceType)
                    put("shipDate", shipment.shipDate)
                    put("requestedShippingDate", shippingDate.toString())
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
                val diagnostic = error.toBookingDiagnostic(shipmentRequest)
                orders.failShipmentBooking(checkoutId, originKey, diagnostic)
                logger.log(Level.SEVERE, "FedEx shipment booking needs review:\n" +
                    "  checkoutId: $checkoutId\n" +
                    "  originKey: $originKey\n" +
                    diagnostic, error)
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
        logger.log(Level.SEVERE, "FedEx shipment preflight needs review checkoutId=$checkoutId", error)
    }

    private fun Exception.toBookingDiagnostic(request: ShipmentRequest): String = when (this) {
        is error.FedExServiceException -> buildString {
            appendLine("    originCountry: ${request.from.countryCode}")
            appendLine("    destinationCountry: ${request.to.countryCode}")
            appendLine("  response:")
            appendLine("    status: ${statusCode ?: "unknown"}")
            appendLine("    sdkCode: ${errorCode.name} (${errorCode.code})")
            appendLine("    message: ${message ?: "FedEx rejected the shipment"}")
            append("    details:\n")
            append(description.toIndentedJson("      ") ?: "      (no response body)")
        }
        else -> "  error: ${javaClass.simpleName}: ${message ?: "FedEx booking outcome requires review"}"
    }

    private fun String?.toIndentedJson(indent: String): String? {
        if (this.isNullOrBlank()) return null
        val formatted = runCatching {
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            Json { prettyPrint = true; prettyPrintIndent = "  " }
                .parseToJsonElement(this).toString()
        }.getOrDefault(this)
        return formatted.lineSequence().joinToString("\n") { "$indent$it" }
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
