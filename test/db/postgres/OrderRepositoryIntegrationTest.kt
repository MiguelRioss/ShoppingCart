package db.postgres

import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

class OrderRepositoryIntegrationTest {
    @Test fun `order survives repository reload and duplicate payment notifications`() {
        assumeTrue(System.getenv("RUN_ORDER_DB_TEST") == "true")
        val database = requireNotNull(Database.fromEnvironment())
        PostgresSchema(database).migrate()
        val user = UUID.randomUUID()
        val session = "cs_order_test_${UUID.randomUUID()}"
        try {
            val repository = PostgresOrderRepository(database)
            repository.create(session, user, buildJsonObject { put("total", "12.50") })
            repository.create(session, user, buildJsonObject { put("total", "99.00") })
            val pending = PostgresOrderRepository(database).list(user).single()
            assertEquals("PENDING_PAYMENT", pending.getValue("status").jsonPrimitive.content)
            assertEquals("12.50", pending.getValue("snapshot").jsonObject.getValue("total").jsonPrimitive.content)
            repeat(2) { assertTrue(repository.updatePayment(session, "PAID", 1250, "eur", "pi_test")) }
            assertTrue(repository.updatePayment(session, "PAYMENT_FAILED", null, null, null))
            val paid = PostgresOrderRepository(database).list(user).single()
            assertEquals("PAID", paid.getValue("status").jsonPrimitive.content)
            assertEquals(1250, paid.getValue("amountPaidMinor").jsonPrimitive.int)
            assertTrue(repository.list(UUID.randomUUID()).isEmpty())
        } finally {
            database.getConnection().use { connection -> connection.prepareStatement("DELETE FROM orders WHERE checkout_session_id = ?").use {
                it.setString(1, session); it.executeUpdate()
            } }
            database.close()
        }
    }
}
