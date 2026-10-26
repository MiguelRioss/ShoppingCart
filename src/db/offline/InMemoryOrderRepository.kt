package db.offline

import db.OrderRepository
import java.util.UUID
import kotlinx.serialization.json.*

class InMemoryOrderRepository : OrderRepository {
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
    @Synchronized override fun list(userId: UUID) = orders.values.filter { it.first == userId }.map { it.second }
}
