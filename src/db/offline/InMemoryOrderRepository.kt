package db.offline

import db.OrderRepository
import java.util.UUID
import kotlinx.serialization.json.*

class InMemoryOrderRepository : OrderRepository {
    private val invoiceNumbers = mutableMapOf<String, String>()
    private var nextInvoiceNumber = 1L
    private val purchaseOrderNumbers = mutableMapOf<String, String>()
    private var nextPurchaseOrderNumber = 1L
    private val shipments = mutableMapOf<Pair<String, String>, JsonObject>()
    @Synchronized override fun invoiceNumberForOrder(orderId: String): String {
        val order = orders[orderId] ?: throw IllegalArgumentException("Order not found: $orderId")
        require(order.second["status"]?.jsonPrimitive?.content == "PAID") { "Invoice number requires a paid order" }
        return invoiceNumbers.getOrPut(orderId) { "INV-${(nextInvoiceNumber++).toString().padStart(10, '0')}" }
    }
    @Synchronized override fun purchaseOrderNumberForOrder(orderId: String): String {
        val order = orders[orderId] ?: throw IllegalArgumentException("Order not found: $orderId")
        require(order.second["status"]?.jsonPrimitive?.content == "PAID") { "Purchase order number requires a paid order" }
        return purchaseOrderNumbers.getOrPut(orderId) { "PO-${(nextPurchaseOrderNumber++).toString().padStart(10, '0')}" }
    }
    @Synchronized override fun claimShipmentBooking(checkoutId: String, originKey: String): Boolean {
        if (orders[checkoutId] == null) return false
        return shipments.putIfAbsent(checkoutId to originKey,
            buildJsonObject { put("status", "BOOKING") }) == null
    }
    @Synchronized override fun completeShipmentBooking(checkoutId: String, originKey: String, booking: JsonObject) {
        val key = checkoutId to originKey
        require(shipments[key] != null) { "Shipment booking was not claimed" }
        shipments[key] = JsonObject(booking + ("status" to JsonPrimitive("BOOKED")))
    }
    @Synchronized override fun failShipmentBooking(checkoutId: String, originKey: String, reason: String) {
        val key = checkoutId to originKey
        if (shipments[key] != null) shipments[key] = buildJsonObject {
            put("status", "NEEDS_REVIEW"); put("reason", reason.take(1000))
        }
    }
    @Synchronized override fun shipmentBookings(checkoutId: String): JsonArray = JsonArray(
        shipments.filterKeys { it.first == checkoutId }.entries.sortedBy { it.key.second }.map { (key, value) ->
            JsonObject(value + ("originKey" to JsonPrimitive(key.second)))
        }
    )
    override fun findByCheckoutForEmail(checkoutId: String): JsonObject? = findForEmail(checkoutId)
    @Synchronized override fun snapshotForCheckout(checkoutId: String): JsonObject? = orders[checkoutId]?.second
    @Synchronized override fun findForEmail(orderId: String): JsonObject? = orders[orderId]?.let { (userId, snapshot) ->
        buildJsonObject {
            put("id", orderId); put("checkoutSessionId", orderId)
            put("userId", userId?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("status", snapshot["status"] ?: JsonPrimitive("PENDING_PAYMENT")); put("snapshot", snapshot)
            for (field in listOf("amountPaidMinor", "paidCurrency", "paidAt")) snapshot[field]?.let { put(field, it) }
            invoiceNumbers[orderId]?.let { put("invoiceNumber", it) }
        }
    }
    private val orders = mutableMapOf<String, Pair<UUID?, JsonObject>>()
    @Synchronized override fun create(checkoutId: String, userId: UUID?, snapshot: JsonObject) {
        orders.putIfAbsent(checkoutId, userId to JsonObject(snapshot + mapOf("checkoutSessionId" to JsonPrimitive(checkoutId), "status" to JsonPrimitive("PENDING_PAYMENT"))))
    }
    @Synchronized override fun updatePayment(checkoutId: String, status: String, amountMinor: Long?, currency: String?, paymentId: String?): Boolean {
        val order = orders[checkoutId] ?: return false
        if (order.second["status"]?.jsonPrimitive?.content != "PAID") {
            orders[checkoutId] = order.first to JsonObject(order.second + mapOf("status" to JsonPrimitive(status), "paymentId" to (paymentId?.let(::JsonPrimitive) ?: JsonNull), "amountPaidMinor" to (amountMinor?.let(::JsonPrimitive) ?: JsonNull), "paidCurrency" to (currency?.let(::JsonPrimitive) ?: JsonNull), "paidAt" to (if (status == "PAID") JsonPrimitive(java.time.Instant.now().toString()) else JsonNull)))
            if (status == "PAID") invoiceNumbers.getOrPut(checkoutId) { "INV-${(nextInvoiceNumber++).toString().padStart(10, '0')}" }
        }
        return true
    }
    @Synchronized override fun listAll(): List<JsonObject> = orders.entries.map { (checkoutId, entry) ->
        buildJsonObject {
            put("items", entry.second["items"] ?: JsonArray(emptyList()))
            put("id", checkoutId)
            put("checkoutSessionId", checkoutId)
            put("userId", entry.first?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("createdAt", entry.second["createdAt"] ?: JsonPrimitive(""))
            for (key in listOf("status", "customerEmail", "total", "currency", "amountPaidMinor", "paidCurrency")) {
                put(key, entry.second[key] ?: JsonNull)
            }
        }
    }
    @Synchronized override fun list(userId: UUID) = orders.values.filter { it.first == userId }.map { it.second }
}
