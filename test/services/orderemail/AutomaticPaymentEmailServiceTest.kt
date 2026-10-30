package services.orderemail

import db.offline.InMemoryOrderRepository
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class AutomaticPaymentEmailServiceTest {
    private val orders = InMemoryOrderRepository()
    private val store = InMemoryPaymentEmailSubmissionStore()
    private val payload = buildJsonObject { put("templateId", "payment-confirmation"); put("idempotencyKey", "fixed") }
    private fun paid() {
        orders.create("checkout", UUID.randomUUID(), buildJsonObject {})
        orders.updatePayment("checkout", "PAID", 1250, "eur", "payment")
    }
    @Test fun `paid order submits once across repeated callbacks and service instances`() {
        paid()
        var sends = 0
        val sender = EmailSenderClient { sends++; buildJsonObject { put("status", "accepted") } }
        repeat(3) { AutomaticPaymentEmailService(orders, store, { payload }, sender).onPaid("checkout") }
        assertEquals(1, sends)
        assertEquals("SUBMITTED", store.state("checkout"))
    }
    @Test fun `unpaid and invalid payloads never claim or send`() {
        orders.create("checkout", UUID.randomUUID(), buildJsonObject {})
        val service = AutomaticPaymentEmailService(orders, store, { throw EmailPayloadValidationException(listOf("data")) }, EmailSenderClient { error("must not send") })
        service.onPaid("checkout")
        assertNull(store.state("checkout"))
        orders.updatePayment("checkout", "PAID", 1250, "eur", null)
        assertFailsWith<EmailPayloadValidationException> { service.onPaid("checkout") }
        assertNull(store.state("checkout"))
    }
    @Test fun `uncertain transport outcome does not resend on webhook retry`() {
        paid()
        var sends = 0
        val service = AutomaticPaymentEmailService(orders, store, { payload }, EmailSenderClient { sends++; throw EmailSenderUnavailableException() })
        repeat(2) { assertFailsWith<PaymentEmailNeedsReviewException> { service.onPaid("checkout") } }
        assertEquals(1, sends)
        assertEquals("UNCERTAIN", store.state("checkout"))
    }
    @Test fun `missing sender configuration can retry after configuration is fixed`() {
        paid()
        val failing = AutomaticPaymentEmailService(orders, store, { payload }, EmailSenderClient { throw EmailSenderConfigurationException() })
        assertFailsWith<EmailSenderConfigurationException> { failing.onPaid("checkout") }
        assertNull(store.state("checkout"))
        AutomaticPaymentEmailService(orders, store, { payload }, EmailSenderClient { buildJsonObject {} }).onPaid("checkout")
        assertEquals("SUBMITTED", store.state("checkout"))
    }
}
