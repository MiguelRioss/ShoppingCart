package http.checkout

import db.offline.InMemoryOrderRepository
import http.HttpRequest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*
import kotlin.test.*

class StripeWebhookHandlerTest {
    private val orders = InMemoryOrderRepository()
    private val user = UUID.randomUUID()
    private val handler = StripeWebhookHandler(orders, { "whsec_test" }, { 1000L })
    private fun body(type: String = "checkout.session.completed", paid: Boolean = true) =
        """{"type":"$type","data":{"object":{"id":"cs_test","payment_status":"${if (paid) "paid" else "unpaid"}","amount_total":1250,"currency":"eur","payment_intent":"pi_test"}}}"""
    private fun request(body: String, timestamp: Long = 1000L): HttpRequest {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("whsec_test".toByteArray(), "HmacSHA256"))
        val signature = mac.doFinal("$timestamp.$body".toByteArray()).joinToString("") { "%02x".format(it) }
        return HttpRequest("POST", "/webhooks/stripe", body, mapOf("Stripe-Signature" to listOf("t=$timestamp,v1=$signature")))
    }
    @Test fun `verified paid event is idempotent and failure cannot downgrade paid order`() {
        orders.create("cs_test", user, buildJsonObject { put("total", "12.50") })
        repeat(2) { assertEquals(200, handler.handle(request(body())).statusCode) }
        assertEquals(200, handler.handle(request(body("checkout.session.async_payment_failed", false))).statusCode)
        assertEquals(1, orders.list(user).size)
        assertEquals("PAID", orders.list(user).single().getValue("status").jsonPrimitive.content)
        assertTrue(orders.list(UUID.randomUUID()).isEmpty())
    }
    @Test fun `unpaid completed event waits for asynchronous payment`() {
        orders.create("cs_test", user, buildJsonObject {})
        assertEquals(200, handler.handle(request(body(paid = false))).statusCode)
        assertEquals("PENDING_PAYMENT", orders.list(user).single().getValue("status").jsonPrimitive.content)
        assertEquals(200, handler.handle(request(body("checkout.session.async_payment_succeeded"))).statusCode)
        assertEquals("PAID", orders.list(user).single().getValue("status").jsonPrimitive.content)
    }
    @Test fun `rejects altered unsigned and expired requests`() {
        val signed = request(body())
        assertEquals(400, handler.handle(signed.copy(body = body().replace("1250", "1"))).statusCode)
        assertEquals(400, handler.handle(signed.copy(headers = emptyMap())).statusCode)
        assertEquals(400, handler.handle(request(body(), 100)).statusCode)
    }
    @Test fun `unknown order asks Stripe to retry and missing secret fails closed`() {
        assertEquals(503, handler.handle(request(body())).statusCode)
        assertEquals(503, StripeWebhookHandler(orders, { null }).handle(request(body())).statusCode)
    }
    @Test fun `expired checkout remains unpaid and unrelated events are acknowledged`() {
        orders.create("cs_test", user, buildJsonObject {})
        assertEquals(200, handler.handle(request(body("checkout.session.expired", false))).statusCode)
        assertEquals("EXPIRED", orders.list(user).single().getValue("status").jsonPrimitive.content)
        assertEquals(200, handler.handle(request(body("customer.created", false))).statusCode)
        assertEquals("EXPIRED", orders.list(user).single().getValue("status").jsonPrimitive.content)
    }
}
