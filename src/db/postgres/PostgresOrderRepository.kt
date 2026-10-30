package db.postgres

import db.OrderRepository
import java.util.UUID
import kotlinx.serialization.json.*

class PostgresOrderRepository(private val database: Database) : OrderRepository {
    override fun findByCheckoutForEmail(checkoutId: String): JsonObject? = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT id, user_id, status, snapshot, amount_paid_minor, paid_currency FROM orders WHERE checkout_session_id = ?").use {
            it.setString(1, checkoutId)
            it.executeQuery().use { rows -> if (!rows.next()) null else buildJsonObject {
                put("id", rows.getString("id")); put("checkoutSessionId", checkoutId)
                put("userId", rows.getString("user_id")?.let(::JsonPrimitive) ?: JsonNull)
                put("status", rows.getString("status")); put("snapshot", Json.parseToJsonElement(rows.getString("snapshot")))
                put("amountPaidMinor", rows.getLong("amount_paid_minor")); put("paidCurrency", rows.getString("paid_currency"))
            } }
        }
    }
    override fun findForEmail(orderId: String): JsonObject? = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT id, checkout_session_id, user_id, status, snapshot FROM orders WHERE id::text = ?").use {
            it.setString(1, orderId)
            it.executeQuery().use { rows -> if (!rows.next()) null else buildJsonObject {
                put("id", rows.getString("id")); put("checkoutSessionId", rows.getString("checkout_session_id"))
                put("userId", rows.getString("user_id")?.let(::JsonPrimitive) ?: JsonNull)
                put("status", rows.getString("status")); put("snapshot", Json.parseToJsonElement(rows.getString("snapshot")))
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
                paid_at = CASE WHEN ? = 'PAID' THEN COALESCE(paid_at, CURRENT_TIMESTAMP) ELSE paid_at END
                WHERE checkout_session_id = ?""").use {
                it.setString(1, status); it.setString(2, status); it.setObject(3, amountMinor)
                it.setString(4, status); it.setString(5, currency); it.setString(6, paymentId)
                it.setString(7, status); it.setString(8, checkoutId); it.executeUpdate() > 0
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
