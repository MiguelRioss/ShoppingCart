package http.checkout

import config.Environment
import db.OrderRepository
import http.*
import java.time.Instant
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*

/** Verifies the exact raw body before trusting any payment status. */
class   StripeWebhookHandler(
    private val orders: OrderRepository,
    private val secret: () -> String? = { Environment.get("STRIPE_WEBHOOK_SECRET") },
    private val now: () -> Long = { Instant.now().epochSecond },
    private val clearPaidCart: (String) -> Unit = {},
    private val onPaymentConfirmed: (String) -> Unit = {},
    private val ukSecret: () -> String? = { Environment.get("STRIPE_UK_WEBHOOK_SECRET") }
) : RequestHandler {
    private val logger = Logger.getLogger(javaClass.name)

    override fun handle(request: HttpRequest): HttpResponse {
        val signingSecrets = listOfNotNull(secret()?.takeIf { it.isNotBlank() }, ukSecret()?.takeIf { it.isNotBlank() })
        if (signingSecrets.isEmpty()) return HttpResponse(503, """{"error":"Stripe webhook secret is not configured"}""")
        return try {
            val parts = request.header("Stripe-Signature").orEmpty().split(',').map { it.trim().split('=', limit = 2) }
            val timestamp = parts.firstOrNull { it.size == 2 && it[0] == "t" }?.get(1)?.toLongOrNull()
            require(timestamp != null && timestamp >= now() - 300 && timestamp <= now() + 300)
            require(signingSecrets.any { signingSecret ->
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(signingSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                val expected = mac.doFinal("$timestamp.${request.body}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                parts.any { it.size == 2 && it[0] == "v1" && MessageDigest.isEqual(expected.toByteArray(), it[1].toByteArray()) }
            })
            val event = Json.parseToJsonElement(request.body).jsonObject
            val type = event["type"]?.jsonPrimitive?.content
            val supported = setOf("checkout.session.completed", "checkout.session.async_payment_succeeded", "checkout.session.async_payment_failed", "checkout.session.expired")
            if (type !in supported) return HttpResponse(200, """{"received":true}""")
            val session = event.getValue("data").jsonObject.getValue("object").jsonObject
            val status = when {
                type == "checkout.session.expired" -> "EXPIRED"
                type == "checkout.session.async_payment_failed" -> "PAYMENT_FAILED"
                type == "checkout.session.async_payment_succeeded" || session["payment_status"]?.jsonPrimitive?.content == "paid" -> "PAID"
                else -> "PENDING_PAYMENT"
            }
            val id = session.getValue("id").jsonPrimitive.content
            val amount = session["amount_total"]?.jsonPrimitive?.longOrNull
            val currency = session["currency"]?.jsonPrimitive?.contentOrNull
            require(status != "PAID" || (amount != null && amount >= 0 && !currency.isNullOrBlank()))
            val eventId = event["id"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            logger.info("Stripe webhook accepted eventId=$eventId type=$type checkoutId=$id paymentStatus=$status")
            if (!orders.updatePayment(id, status, amount, currency, session["payment_intent"]?.jsonPrimitive?.contentOrNull)) {
                // Let Stripe retry if its notification raced checkout persistence.
                return HttpResponse(503, """{"error":"Order not available yet"}""")
            }
            if (status == "PAID") {
                logger.info("Stripe payment persisted checkoutId=$id; clearing cart")
                clearPaidCart(id)
                try {
                    logger.info("Paid checkout email callback started checkoutId=$id")
                    onPaymentConfirmed(id)
                    logger.info("Paid checkout email callback completed checkoutId=$id")
                }
                catch (error: Exception) {
                    logger.log(Level.SEVERE, "Stripe payment confirmation failed checkoutId=$id", error)
                    return HttpResponse(503, """{"error":"Payment saved; confirmation email pending or requires review"}""")
                }
            }
            HttpResponse(200, """{"received":true}""")
        } catch (_: IllegalArgumentException) {
            HttpResponse(400, """{"error":"Invalid Stripe webhook"}""")
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Stripe webhook order update failed", error)
            HttpResponse(500, """{"error":"Order update failed"}""")
        }
    }
}
