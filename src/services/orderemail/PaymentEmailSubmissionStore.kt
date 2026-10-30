package services.orderemail

import db.postgres.Database

/** Durable claim prevents parallel/repeated Stripe events from submitting the same email. */
interface PaymentEmailSubmissionStore {
    fun claim(checkoutId: String): Boolean
    fun complete(checkoutId: String)
    fun uncertain(checkoutId: String)
    fun state(checkoutId: String): String?
    fun release(checkoutId: String)
}

class InMemoryPaymentEmailSubmissionStore : PaymentEmailSubmissionStore {
    private val states = mutableMapOf<String, String>()
    @Synchronized override fun claim(checkoutId: String): Boolean {
        if (states.containsKey(checkoutId)) return false
        states[checkoutId] = "SUBMITTING"
        return true
    }
    @Synchronized override fun complete(checkoutId: String) { states[checkoutId] = "SUBMITTED" }
    @Synchronized override fun uncertain(checkoutId: String) { states[checkoutId] = "UNCERTAIN" }
    @Synchronized override fun state(checkoutId: String) = states[checkoutId]
    @Synchronized override fun release(checkoutId: String) { states.remove(checkoutId) }
}

class PostgresPaymentEmailSubmissionStore(private val database: Database) : PaymentEmailSubmissionStore {
    override fun release(checkoutId: String) {
        database.getConnection().use { connection ->
            connection.prepareStatement("DELETE FROM payment_email_submissions WHERE checkout_session_id = ? AND status = 'SUBMITTING'").use {
                it.setString(1, checkoutId); it.executeUpdate()
            }
        }
    }
    override fun claim(checkoutId: String): Boolean = database.getConnection().use { connection ->
        connection.prepareStatement("INSERT INTO payment_email_submissions (checkout_session_id, status) VALUES (?, 'SUBMITTING') ON CONFLICT DO NOTHING").use {
            it.setString(1, checkoutId)
            it.executeUpdate() == 1
        }
    }
    private fun update(checkoutId: String, status: String) {
        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE payment_email_submissions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE checkout_session_id = ?").use {
                it.setString(1, status); it.setString(2, checkoutId); check(it.executeUpdate() == 1)
            }
        }
    }
    override fun complete(checkoutId: String) = update(checkoutId, "SUBMITTED")
    override fun uncertain(checkoutId: String) = update(checkoutId, "UNCERTAIN")
    override fun state(checkoutId: String): String? = database.getConnection().use { connection ->
        connection.prepareStatement("SELECT status FROM payment_email_submissions WHERE checkout_session_id = ?").use {
            it.setString(1, checkoutId)
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }
}
