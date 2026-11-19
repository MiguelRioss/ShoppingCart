package db

import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray

interface OrderRepository {
    fun findByCheckoutForEmail(checkoutId: String): JsonObject?
    fun findForEmail(orderId: String): JsonObject?

    fun snapshotForCheckout(checkoutId: String): JsonObject?

    fun create(checkoutId: String, userId: UUID?, snapshot: JsonObject)
    fun invoiceNumberForOrder(orderId: String): String
    fun purchaseOrderNumberForOrder(orderId: String): String
    fun claimShipmentBooking(checkoutId: String, originKey: String): Boolean
    fun completeShipmentBooking(checkoutId: String, originKey: String, booking: JsonObject)
    fun failShipmentBooking(checkoutId: String, originKey: String, reason: String)
    fun shipmentBookings(checkoutId: String): JsonArray
    /** Returns false if checkout creation has not finished saving the order yet. */
    fun updatePayment(checkoutId: String, status: String, amountMinor: Long?, currency: String?, paymentId: String?): Boolean
    fun listAll(): List<JsonObject>
    fun list(userId: UUID): List<JsonObject>
}
