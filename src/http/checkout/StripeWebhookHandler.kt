package http.checkout

import config.Environment
import db.OrderRepository
import http.*
import java.time.Instant
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*

/** Verifies the exact raw body before trusting any payment status. */
class StripeWebhookHandler(
    private val orders: OrderRepository,
    private val secret: () -> String? = { Environment.get("STRIPE_WEBHOOK_SECRET") },
    private val now: () -> Long = { Instant.now().epochSecond },
    private val onPaymentConfirmed: (String) -> Unit = {}
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val signingSecret = secret()?.takeIf { it.isNotBlank() }
            ?: return HttpResponse(503, """{"error":"Stripe webhook secret is not configured"}""")
        return try {
            val parts = request.header("Stripe-Signature").orEmpty().split(',').map { it.trim().split('=', limit = 2) }
            val timestamp = parts.firstOrNull { it.size == 2 && it[0] == "t" }?.get(1)?.toLongOrNull()
            require(timestamp != null && timestamp >= now() - 300 && timestamp <= now() + 300)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(signingSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val expected = mac.doFinal("$timestamp.${request.body}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            require(parts.any { it.size == 2 && it[0] == "v1" && MessageDigest.isEqual(expected.toByteArray(), it[1].toByteArray()) })
            val event = Json.parseToJsonElement(request.body).jsonObject
            val type = event["type"]?.jsonPrimitive?.content
            val supported = setOf("checkout.session.completed", "checkout.session.async_payment_succeeded", "checkout.session.async_payment_failed", "checkout.session.expired")
            if (type !in supported) return HttpResponse(200, """{"received":true}""")
            val session = event.getValue("data").jsonObject.getValue("object").jsonObject
            val status = when {
                type == "checkout.session.expired" -> "EXPIRED"
                type == "checkout.session.async_payment_failed" -> "PAYMENT_FAILED"
                session["payment_status"]?.jsonPrimitive?.content == "paid" -> "PAID"
                else -> "PENDING_PAYMENT"
            }
            val id = session.getValue("id").jsonPrimitive.content
            val amount = session["amount_total"]?.jsonPrimitive?.longOrNull
            val currency = session["currency"]?.jsonPrimitive?.contentOrNull
            require(status != "PAID" || (amount != null && amount >= 0 && !currency.isNullOrBlank()))
            if (!orders.updatePayment(id, status, amount, currency, session["payment_intent"]?.jsonPrimitive?.contentOrNull)) {
                // Let Stripe retry if its notification raced checkout persistence.
                return HttpResponse(503, """{"error":"Order not available yet"}""")
            }
            if (status == "PAID") {
                try { onPaymentConfirmed(id) }
                catch (_: Exception) { return HttpResponse(503, """{"error":"Payment saved; confirmation email pending or requires review"}""") }
            }
            HttpResponse(200, """{"received":true}""")
        } catch (_: IllegalArgumentException) {
            HttpResponse(400, """{"error":"Invalid Stripe webhook"}""")
        } catch (_: Exception) {
            HttpResponse(500, """{"error":"Order update failed"}""")
        }
    }
}
