package http.checkout

import db.offline.InMemoryOrderRepository
import http.HttpRequest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*
import kotlin.test.*

class StripeWebhookHandlerTest {
    @Test fun `completed and asynchronous success persist payment idempotently`() {
        for (eventType in listOf("checkout.session.completed", "checkout.session.async_payment_succeeded")) {
            val orders = InMemoryOrderRepository()
            val checkoutId = "cs_test_${UUID.randomUUID()}"
            orders.create(checkoutId, null, buildJsonObject {})
            val body = """{"type":"$eventType","data":{"object":{"id":"$checkoutId","payment_status":"${if (eventType == "checkout.session.completed") "paid" else "unpaid"}","amount_total":15552,"currency":"eur"}}}"""
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec("test-secret".toByteArray(), "HmacSHA256"))
            val signature = mac.doFinal("1000.$body".toByteArray()).joinToString("") { "%02x".format(it) }
            val request = HttpRequest("POST", "/webhooks/stripe", body, mapOf("Stripe-Signature" to listOf("t=1000,v1=$signature")))
            val handler = StripeWebhookHandler(orders, { "test-secret" }, { 1000 })
            repeat(2) { assertEquals(200, handler.handle(request).statusCode) }
            val order = orders.findByCheckoutForEmail(checkoutId)!!
            assertEquals("PAID", order["status"]!!.jsonPrimitive.content)
            assertEquals(15552, order["amountPaidMinor"]!!.jsonPrimitive.long)
            assertEquals("eur", order["paidCurrency"]!!.jsonPrimitive.content)
        }
    }
}
