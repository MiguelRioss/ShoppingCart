package db.postgres

import db.OrderRepository
import java.util.UUID
import kotlinx.serialization.json.*

class PostgresOrderRepository(private val database: Database) : OrderRepository {
    override fun claimShipmentBooking(checkoutId: String, originKey: String): Boolean = database.getConnection().use { connection ->
        connection.prepareStatement("INSERT INTO shipment_bookings (checkout_session_id,origin_key,status) VALUES (?,?,'BOOKING') ON CONFLICT DO NOTHING").use {
            it.setString(1, checkoutId); it.setString(2, originKey); it.executeUpdate() == 1
        }
    }
    override fun completeShipmentBooking(checkoutId: String, originKey: String, booking: JsonObject) {
        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE shipment_bookings SET status='BOOKED',booking=?::jsonb,error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE checkout_session_id=? AND origin_key=? AND status='BOOKING'").use {
                it.setString(1, booking.toString()); it.setString(2, checkoutId); it.setString(3, originKey)
                check(it.executeUpdate() == 1) { "Shipment booking claim is missing or already finalized" }
            }
        }
    }
    override fun failShipmentBooking(checkoutId: String, originKey: String, reason: String) {
        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE shipment_bookings SET status='NEEDS_REVIEW',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE checkout_session_id=? AND origin_key=? AND status='BOOKING'").use {
                it.setString(1, reason.take(1000)); it.setString(2, checkoutId); it.setString(3, originKey); it.executeUpdate()
            }
        }
    }
    override fun shipmentBookings(checkoutId: String): JsonArray = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT origin_key,status,booking,error_message FROM shipment_bookings WHERE checkout_session_id=? ORDER BY origin_key").use {
            it.setString(1, checkoutId)
            it.executeQuery().use { rows -> buildJsonArray {
                while (rows.next()) add(buildJsonObject {
                    put("originKey", rows.getString("origin_key")); put("status", rows.getString("status"))
                    rows.getString("booking")?.let { json -> put("booking", Json.parseToJsonElement(json)) }
                    rows.getString("error_message")?.let { message -> put("reason", message) }
                })
            } }
        }
    }
    override fun invoiceNumberForOrder(orderId: String): String = database.getConnection().use { connection ->
        connection.prepareStatement("UPDATE orders SET invoice_number = nextval('order_invoice_number_seq') WHERE id::text = ? AND status = 'PAID' AND invoice_number IS NULL RETURNING invoice_number").use { statement ->
            statement.setString(1, orderId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) }
        }
        connection.prepareStatement("SELECT invoice_number, status FROM orders WHERE id::text = ?").use { statement ->
            statement.setString(1, orderId)
            statement.executeQuery().use { rows ->
                require(rows.next()) { "Order not found: $orderId" }
                require(rows.getString("status") == "PAID") { "Invoice number requires a paid order" }
                val number = rows.getLong("invoice_number")
                require(!rows.wasNull()) { "Invoice number could not be assigned" }
                "INV-${number.toString().padStart(10, '0')}"
            }
        }
    }
    override fun purchaseOrderNumberForOrder(orderId: String): String = database.getConnection().use { connection ->
        connection.prepareStatement("UPDATE orders SET purchase_order_number = nextval('order_purchase_number_seq') WHERE id::text = ? AND status = 'PAID' AND purchase_order_number IS NULL RETURNING purchase_order_number").use { statement ->
            statement.setString(1, orderId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) }
        }
        connection.prepareStatement("SELECT purchase_order_number, status FROM orders WHERE id::text = ?").use { statement ->
            statement.setString(1, orderId)
            statement.executeQuery().use { rows ->
                require(rows.next()) { "Order not found: $orderId" }
                require(rows.getString("status") == "PAID") { "Purchase order number requires a paid order" }
                val number = rows.getLong("purchase_order_number")
                require(!rows.wasNull()) { "Purchase order number could not be assigned" }
                "PO-${number.toString().padStart(10, '0')}"
            }
        }
    }
    override fun findByCheckoutForEmail(checkoutId: String): JsonObject? = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT id, user_id, status, snapshot, amount_paid_minor, paid_currency, paid_at FROM orders WHERE checkout_session_id = ?").use {
            it.setString(1, checkoutId)
            it.executeQuery().use { rows -> if (!rows.next()) null else buildJsonObject {
                put("id", rows.getString("id")); put("checkoutSessionId", checkoutId)
                put("userId", rows.getString("user_id")?.let(::JsonPrimitive) ?: JsonNull)
                put("status", rows.getString("status")); put("snapshot", Json.parseToJsonElement(rows.getString("snapshot")))
                rows.getLong("amount_paid_minor").takeUnless { rows.wasNull() }?.let { amount -> put("amountPaidMinor", amount) }
                rows.getString("paid_currency")?.let { paidCurrency -> put("paidCurrency", paidCurrency) }
                rows.getTimestamp("paid_at")?.toInstant()?.toString()?.let { paidAt -> put("paidAt", paidAt) }
            } }
        }
    }
    override fun findForEmail(orderId: String): JsonObject? = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT id, checkout_session_id, user_id, status, snapshot, invoice_number FROM orders WHERE id::text = ?").use {
            it.setString(1, orderId)
            it.executeQuery().use { rows -> if (!rows.next()) null else buildJsonObject {
                put("id", rows.getString("id")); put("checkoutSessionId", rows.getString("checkout_session_id"))
                put("userId", rows.getString("user_id")?.let(::JsonPrimitive) ?: JsonNull)
                put("status", rows.getString("status")); put("snapshot", Json.parseToJsonElement(rows.getString("snapshot")))
                rows.getLong("invoice_number").takeUnless { rows.wasNull() }?.let { number -> put("invoiceNumber", "INV-${number.toString().padStart(10, '0')}") }
            } }
        }
    }
    override fun snapshotForCheckout(checkoutId: String): JsonObject? = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT snapshot FROM orders WHERE checkout_session_id = ?").use {
            it.setString(1, checkoutId)
            it.executeQuery().use { rows -> if (rows.next()) Json.parseToJsonElement(rows.getString("snapshot")).jsonObject else null }
        }
    }
    override fun create(checkoutId: String, userId: UUID?, snapshot: JsonObject) {
        database.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO orders (id, checkout_session_id, user_id, snapshot) VALUES (?, ?, ?, ?::jsonb) ON CONFLICT (checkout_session_id) DO NOTHING").use {
                it.setObject(1, UUID.randomUUID()); it.setString(2, checkoutId); it.setObject(3, userId); it.setString(4, snapshot.toString()); it.executeUpdate()
            }
        }
    }
    override fun updatePayment(checkoutId: String, status: String, amountMinor: Long?, currency: String?, paymentId: String?): Boolean =
        database.getConnection().use { connection ->
            connection.prepareStatement("""UPDATE orders SET
                status = CASE WHEN status = 'PAID' THEN status ELSE ? END,
                amount_paid_minor = CASE WHEN ? = 'PAID' THEN ? ELSE amount_paid_minor END,
                paid_currency = CASE WHEN ? = 'PAID' THEN ? ELSE paid_currency END,
                payment_id = COALESCE(?, payment_id),
                paid_at = CASE WHEN ? = 'PAID' THEN COALESCE(paid_at, CURRENT_TIMESTAMP) ELSE paid_at END,
                invoice_number = CASE WHEN ? = 'PAID' THEN COALESCE(invoice_number, nextval('order_invoice_number_seq')) ELSE invoice_number END
                WHERE checkout_session_id = ?""").use {
                it.setString(1, status); it.setString(2, status); it.setObject(3, amountMinor)
                it.setString(4, status); it.setString(5, currency); it.setString(6, paymentId)
                it.setString(7, status); it.setString(8, status); it.setString(9, checkoutId); it.executeUpdate() > 0
            }
        }
    override fun listAll(): List<JsonObject> = database.getConnection().use { connection ->
        connection.prepareStatement("""SELECT id, checkout_session_id, user_id, status, created_at,
            snapshot->>'customerEmail' AS customer_email, snapshot->>'total' AS total,
            snapshot->>'currency' AS currency, snapshot->'items' AS items, amount_paid_minor, paid_currency
            FROM orders ORDER BY created_at DESC, id""").use { statement ->
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add(buildJsonObject {
                    put("items", rows.getString("items")?.let { Json.parseToJsonElement(it) } ?: JsonArray(emptyList()))
                    put("id", rows.getString("id"))
                    put("checkoutSessionId", rows.getString("checkout_session_id"))
                    put("userId", rows.getString("user_id")?.let(::JsonPrimitive) ?: JsonNull)
                    put("status", rows.getString("status"))
                    put("createdAt", rows.getString("created_at"))
                    put("customerEmail", rows.getString("customer_email")?.let(::JsonPrimitive) ?: JsonNull)
                    put("total", rows.getString("total")?.let(::JsonPrimitive) ?: JsonNull)
                    put("currency", rows.getString("currency")?.let(::JsonPrimitive) ?: JsonNull)
                    put("amountPaidMinor", rows.getString("amount_paid_minor")?.toLong()?.let(::JsonPrimitive) ?: JsonNull)
                    put("paidCurrency", rows.getString("paid_currency")?.let(::JsonPrimitive) ?: JsonNull)
                })
            } }
        }
    }

    override fun list(userId: UUID): List<JsonObject> = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC").use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add(buildJsonObject {
                    put("id", rows.getString("id")); put("checkoutSessionId", rows.getString("checkout_session_id"))
                    put("status", rows.getString("status")); put("createdAt", rows.getString("created_at"))
                    put("snapshot", Json.parseToJsonElement(rows.getString("snapshot")))
                    rows.getString("payment_id")?.let { put("paymentId", it) }
                    rows.getString("paid_currency")?.let { put("paidCurrency", it) }
                    rows.getString("amount_paid_minor")?.let { put("amountPaidMinor", it.toLong()) }
                })
            } }
        }
    }
}
