package services.orderemail

import db.OrderRepository
import java.util.Base64
import java.util.Currency
import kotlinx.serialization.json.*
import services.order.OrderManager
import services.pdf.generation.OrderPdfGenerator
import java.time.LocalDate

/** Checkout facts and generated documents. EMAIL_SENDER chooses how to render them. */
class PaidOrderEmailPayloadMapper(
    private val orders: OrderRepository,
    private val orderManager: OrderManager,
    private val pdfGenerator: OrderPdfGenerator = OrderPdfGenerator(orderManager)
) {
    fun fromPaidCheckout(
        checkoutId: String,
        documentTypes: Set<String> = setOf("invoice", "purchase-order", "packing-list", "delivery-note"),
        shippingDate: LocalDate? = null
    ): JsonObject {
        val order = orders.findByCheckoutForEmail(checkoutId)
            ?: throw IllegalArgumentException("Order not found for checkout $checkoutId")
        require(order.text("status") == "PAID") { "Client journey requires a paid order" }
        val id = order.text("id") ?: missing("order.id")
        order.text("checkoutSessionId") ?: missing("order.checkoutSessionId")
        order.text("paidAt") ?: missing("order.paidAt")
        val snapshot = order["snapshot"] as? JsonObject ?: missing("order.snapshot")
        val currency = snapshot.text("currency")?.uppercase() ?: missing("order.snapshot.currency")
        val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()
            ?.takeIf { it >= 0 } ?: missing("order.snapshot.currency")
        val total = snapshot.text("total")?.toBigDecimalOrNull() ?: missing("order.snapshot.total")
        val paid = order.text("amountPaidMinor")?.toLongOrNull() ?: missing("order.amountPaidMinor")
        val subtotalMinor = total.movePointRight(digits).longValueExact()
        require(paid >= subtotalMinor) { "Paid amount is less than order total" }
        require(order.text("paidCurrency")?.uppercase() == currency) { "Paid currency does not match order" }
        val paidSnapshot = orderManager.withProductLeadTimes(paidSnapshot(snapshot, paid, subtotalMinor, digits))
        val email = paidSnapshot.text("customerEmail") ?: missing("order.snapshot.customerEmail")
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) { "Invalid customer email" }
        snapshot.text("customerFirstName") ?: missing("order.snapshot.customerFirstName")
        snapshot.text("productSubtotal") ?: missing("order.snapshot.productSubtotal")
        snapshot.text("shippingTotal") ?: missing("order.snapshot.shippingTotal")
        val address = snapshot["deliveryAddress"] as? JsonObject ?: missing("order.snapshot.deliveryAddress")
        for (field in listOf("addressLine1", "townOrCity", "postcode", "country"))
            address.text(field) ?: missing("order.snapshot.deliveryAddress.$field")
        val items = (paidSnapshot["items"] as? JsonArray)?.map { it.jsonObject }
            ?.filter { it.text("productId") != "0" }.orEmpty()
        require(items.isNotEmpty()) { "Order has no products" }
        val cartProducts = (snapshot["cartProducts"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
        items.forEachIndexed { index, item ->
            val productId = item.text("productId") ?: missing("order.snapshot.items[$index].productId")
            for (field in listOf("name", "amountTotal", "currency", "imageUrl", "generalLeadTime"))
                item.text(field) ?: missing("order.snapshot.items[$index].$field")
            val cart = cartProducts.find { it.text("productId") == productId }
                ?: missing("order.snapshot.cartProducts[$index]")
            val field = if (cart["isSample"]?.jsonPrimitive?.booleanOrNull == true) "sampleUnits" else "squareMeters"
            cart.text(field) ?: missing("order.snapshot.cartProducts[$index].$field")
        }
        orderManager.despatchEstimate(id, paidSnapshot) ?: missing("order.snapshot.items.generalLeadTime")
        val number = orderManager.invoiceNumber(id)
        val orderManagerPo = "PO-$id"
        val pdfs = pdfGenerator.generate(id, paidSnapshot, documentTypes, shippingDate)
        val shipmentBookings = orders.shipmentBookings(checkoutId).mapNotNull { it as? JsonObject }
        val awb = shipmentBookings.mapNotNull { entry ->
            if (entry.text("status") != "BOOKED") return@mapNotNull null
            (entry["booking"] as? JsonObject)?.text("masterTrackingNumber")
        }.distinct().joinToString(", ")
        val trackingNumbers = shipmentBookings.flatMap { entry ->
            if (entry.text("status") != "BOOKED") return@flatMap emptyList()
            val booking = entry["booking"] as? JsonObject ?: return@flatMap emptyList()
            (booking["packages"] as? JsonArray).orEmpty().mapNotNull { packageInfo ->
                (packageInfo as? JsonObject)?.text("trackingNumber")
            }
        }.distinct().ifEmpty { awb.takeIf(String::isNotBlank)?.let(::listOf).orEmpty() }.joinToString(", ")
        return buildJsonObject {
            put("schemaVersion", 1)
            put("eventId", "paid:$id")
            put("idempotencyKey", "order:$id:start-client-journey")
            put("order", JsonObject(order + mapOf(
                "invoiceNumber" to JsonPrimitive(number),
                "purchaseOrderNumber" to JsonPrimitive(orderManagerPo),
                "snapshot" to paidSnapshot
            )))
            put("documents", buildJsonObject {
                pdfs.forEach { (type, document) ->
                    put(type, buildJsonObject {
                        if (type == "invoice") put("number", number)
                        put("filename", document.filename)
                        put("contentType", "application/pdf")
                        put("contentBase64", Base64.getEncoder().encodeToString(document.bytes))
                    })
                }
            })
            val supplier = orderManager.supplierContact(id)
            val productLines = items.map { item ->
                val cart = cartProducts.first { it.text("productId") == item.text("productId") }
                buildJsonObject {
                    put("name", item.getValue("name"))
                    put("quantity", cart.getValue(if (cart["isSample"]?.jsonPrimitive?.booleanOrNull == true) "sampleUnits" else "squareMeters"))
                    put("unit", if (cart["isSample"]?.jsonPrimitive?.booleanOrNull == true) "pieces" else "m²")
                    put("lineTotal", item.getValue("amountTotal"))
                    put("currency", item.getValue("currency"))
                }
            }
            val boxes = cartProducts.sumOf { it.text("amountBoxes")?.toIntOrNull() ?: 0 }
            val area = cartProducts.sumOf { it.text("squareMeters")?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO }
            put("fulfilment", buildJsonObject {
                put("purchase-order", buildJsonObject {
                    put("products", JsonArray(productLines)); put("boxes", boxes)
                    put("po_number", orderManagerPo); put("required_despatch_date", orderManager.despatchEstimate(id, paidSnapshot)!!)
                    put("supplier_contact_name", supplier.first); put("supplier_email", supplier.second)
                })
                put("packing-list", buildJsonObject {
                    put("products", JsonArray(productLines)); put("boxes", boxes)
                    put("packing_list_number", "PL-$id"); put("quantity_m2", area.toPlainString()); put("quantity_units", boxes)
                    put("gross_weight", "See attached packing list"); put("package_count", "See attached packing list")
                    put("package_dimensions", "See attached packing list")
                    put("supplier_contact_name", supplier.first); put("supplier_email", supplier.second)
                })
                put("delivery-note", buildJsonObject {
                    put("courier", "See attached delivery note"); put("delivery_note_number", "DN-$id"); put("incoterm", "DDP")
                    put("supplier_contact_name", supplier.first); put("supplier_email", supplier.second)
                    put("tracking_number", trackingNumbers.ifBlank { "Pending carrier booking" })
                    put("awb", awb)
                })
            })
        }
    }

    fun fromPaidOrder(
        orderId: String,
        documentTypes: Set<String>,
        shippingDate: LocalDate? = null
    ): JsonObject {
        val order = orders.findForEmail(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val checkoutId = order.text("checkoutSessionId") ?: missing("order.checkoutSessionId")
        return fromPaidCheckout(checkoutId, documentTypes, shippingDate)
    }

    internal fun paidSnapshot(snapshot: JsonObject, paidMinor: Long, subtotalMinor: Long, digits: Int): JsonObject {
        val scale = java.math.BigDecimal.TEN.pow(digits)
        val tax = java.math.BigDecimal.valueOf(paidMinor - subtotalMinor).divide(scale)
        val paidTotal = java.math.BigDecimal.valueOf(paidMinor).divide(scale)
        return JsonObject(snapshot + mapOf(
            "preTaxTotal" to snapshot.getValue("total"),
            "taxAmount" to JsonPrimitive(tax.toPlainString()),
            "taxCalculation" to JsonPrimitive("stripe_checkout_difference"),
            "total" to JsonPrimitive(paidTotal.toPlainString())
        ))
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    private fun missing(field: String): Nothing = throw IllegalArgumentException("Missing client journey data: $field")
}
