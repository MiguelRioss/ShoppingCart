package db.offline

import db.OrderRepository
import java.util.UUID
import kotlinx.serialization.json.*

class InMemoryOrderRepository : OrderRepository {
    @Synchronized override fun snapshotForCheckout(checkoutId: String): JsonObject? = orders[checkoutId]?.second
    @Synchronized override fun findForEmail(orderId: String): JsonObject? = orders[orderId]?.let { (userId, snapshot) ->
        buildJsonObject {
            put("id", orderId); put("checkoutSessionId", orderId)
            put("userId", userId?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("status", snapshot["status"] ?: JsonPrimitive("PENDING_PAYMENT")); put("snapshot", snapshot)
        }
    }
    private val orders = mutableMapOf<String, Pair<UUID?, JsonObject>>()
    @Synchronized override fun create(checkoutId: String, userId: UUID?, snapshot: JsonObject) {
        orders.putIfAbsent(checkoutId, userId to JsonObject(snapshot + mapOf("checkoutSessionId" to JsonPrimitive(checkoutId), "status" to JsonPrimitive("PENDING_PAYMENT"))))
    }
    @Synchronized override fun updatePayment(checkoutId: String, status: String, amountMinor: Long?, currency: String?, paymentId: String?): Boolean {
        val order = orders[checkoutId] ?: return false
        if (order.second["status"]?.jsonPrimitive?.content != "PAID") {
            orders[checkoutId] = order.first to JsonObject(order.second + mapOf("status" to JsonPrimitive(status), "paymentId" to (paymentId?.let(::JsonPrimitive) ?: JsonNull)))
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
