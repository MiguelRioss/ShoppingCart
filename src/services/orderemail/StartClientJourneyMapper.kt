package services.orderemail

import db.OrderRepository
import java.util.Base64
import java.util.Currency
import kotlinx.serialization.json.*
import services.order.OrderManager

/** Checkout facts and generated documents. EMAIL_SENDER chooses how to render them. */
class StartClientJourneyMapper(
    private val orders: OrderRepository,
    private val orderManager: OrderManager
) {
    fun fromPaidCheckout(checkoutId: String): JsonObject {
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
        val paidSnapshot = paidSnapshot(snapshot, paid, subtotalMinor, digits)
        val email = snapshot.text("customerEmail") ?: missing("order.snapshot.customerEmail")
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) { "Invalid customer email" }
        snapshot.text("customerFirstName") ?: missing("order.snapshot.customerFirstName")
        snapshot.text("productSubtotal") ?: missing("order.snapshot.productSubtotal")
        snapshot.text("shippingTotal") ?: missing("order.snapshot.shippingTotal")
        val address = snapshot["deliveryAddress"] as? JsonObject ?: missing("order.snapshot.deliveryAddress")
        for (field in listOf("addressLine1", "townOrCity", "postcode", "country"))
            address.text(field) ?: missing("order.snapshot.deliveryAddress.$field")
        val items = (snapshot["items"] as? JsonArray)?.map { it.jsonObject }
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
        orderManager.despatchEstimate(id) ?: missing("order.snapshot.items.generalLeadTime")
        val number = orderManager.invoiceNumber(id)
        val pdf = orderManager.invoicePreview(id, paidSnapshot)
        require(pdf.size >= 5 && pdf.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") {
            "Invoice generator did not return PDF bytes"
        }
        return buildJsonObject {
            put("schemaVersion", 1)
            put("eventId", "paid:$id")
            put("idempotencyKey", "order:$id:start-client-journey")
            put("order", JsonObject(order + mapOf(
                "invoiceNumber" to JsonPrimitive(number),
                "snapshot" to paidSnapshot
            )))
            put("documents", buildJsonObject {
                put("invoice", buildJsonObject {
                    put("number", number)
                    put("filename", "invoice.pdf")
                    put("contentType", "application/pdf")
                    put("contentBase64", Base64.getEncoder().encodeToString(pdf))
                })
            })
        }
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
