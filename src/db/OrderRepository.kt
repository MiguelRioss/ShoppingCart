package db

import java.util.UUID
import kotlinx.serialization.json.JsonObject

interface OrderRepository {
    fun create(checkoutId: String, userId: UUID?, snapshot: JsonObject)
    /** Returns false if checkout creation has not finished saving the order yet. */
    fun updatePayment(checkoutId: String, status: String, amountMinor: Long?, currency: String?, paymentId: String?): Boolean
    fun list(userId: UUID): List<JsonObject>
}
