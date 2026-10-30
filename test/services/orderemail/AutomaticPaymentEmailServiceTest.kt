package services.orderemail

import db.offline.InMemoryOrderRepository
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class AutomaticPaymentEmailServiceTest {
    private val orders = InMemoryOrderRepository()
    private val payload = buildJsonObject { put("templateId", "payment-confirmation"); put("idempotencyKey", "fixed") }
    private fun paid() {
        orders.create("checkout", UUID.randomUUID(), buildJsonObject {})
        orders.updatePayment("checkout", "PAID", 1250, "eur", "payment")
    }
    @Test fun repeatedEventsForwardTheSameKeyForEmailSenderToDeduplicate() {
        paid()
        val requests = mutableListOf<JsonObject>()
        repeat(3) {
            AutomaticPaymentEmailService(orders, { payload }, EmailSenderClient { requests += it; buildJsonObject {} }).onPaid("checkout")
        }
        assertEquals(3, requests.size)
        assertEquals(1, requests.map { it.getValue("idempotencyKey") }.distinct().size)
    }
    @Test fun unpaidAndInvalidPayloadsNeverSend() {
        orders.create("checkout", UUID.randomUUID(), buildJsonObject {})
        val service = AutomaticPaymentEmailService(orders, { throw EmailPayloadValidationException(listOf("data")) }, EmailSenderClient { error("must not send") })
        service.onPaid("checkout")
        orders.updatePayment("checkout", "PAID", 1250, "eur", null)
        assertFailsWith<EmailPayloadValidationException> { service.onPaid("checkout") }
    }
    @Test fun upstreamFailurePropagatesForStripeRetry() {
        paid()
        val service = AutomaticPaymentEmailService(orders, { payload }, EmailSenderClient { throw EmailSenderUnavailableException(503) })
        assertFailsWith<EmailSenderUnavailableException> { service.onPaid("checkout") }
    }
}
