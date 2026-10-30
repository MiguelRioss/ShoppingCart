package services.checkout.core

import ShoppingCartProduct
import db.offline.InMemoryOrderRepository
import db.offline.InMemoryShoppingCartRepository
import domain.cart.ShoppingCart
import http.HttpRequest
import http.checkout.StripeWebhookHandler
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*
import kotlin.test.*

class PaidOrderCartCleanerTest {
    private val carts = InMemoryShoppingCartRepository()
    private val orders = InMemoryOrderRepository()
    private val cleaner = PaidOrderCartCleaner(orders, carts)
    private val userId = UUID.randomUUID()
    private val first = ShoppingCart(UUID.randomUUID(), userId, LocalDateTime.parse("2026-10-30T10:00:00"), "first", listOf(ShoppingCartProduct(9278, 0.5, 1, BigDecimal("90.00"), false)))
    private val second = first.copy(id = UUID.randomUUID(), sessionId = "second")
    private val handler = StripeWebhookHandler(orders, {"test-secret"}, {1000}, cleaner::clear)
    private fun saveOrder(legacy: Boolean = false) {
        carts.saveCart(first); carts.saveCart(second)
        orders.create("checkout-first", userId, buildJsonObject {
            put("cartId", first.id.toString())
            if (!legacy) put("cartUpdatedAt", first.dateTime.toString())
            put("cartProducts", cartProductsSnapshot(first))
        })
    }
    private fun event(type: String = "checkout.session.completed", paid: Boolean = true): HttpRequest {
        val body = """{"type":"$type","data":{"object":{"id":"checkout-first","payment_status":"${if(paid) "paid" else "unpaid"}","amount_total":9000,"currency":"eur"}}}"""
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("test-secret".toByteArray(), "HmacSHA256"))
        val signature = mac.doFinal("1000.$body".toByteArray()).joinToString("") { "%02x".format(it) }
        return HttpRequest("POST", "/webhooks/stripe", body, mapOf("Stripe-Signature" to listOf("t=1000,v1=$signature")))
    }
    @Test fun paidOrderClearsOnlyItsCartAndRetriesDoNotClearNewCarts() {
        saveOrder()
        assertEquals(200, handler.handle(event()).statusCode)
        assertNull(carts.getCartBySessionId("first"))
        assertEquals(second, carts.getCartBySessionId("second"))
        val fresh = first.copy(id = UUID.randomUUID(), dateTime = first.dateTime.plusHours(1))
        carts.saveCart(fresh)
        assertEquals(200, handler.handle(event()).statusCode)
        assertEquals(fresh, carts.getCartBySessionId("first"))
    }
    @Test fun delayedPaymentPreservesEditedPurchasedCart() {
        saveOrder()
        val edited = first.copy(dateTime = first.dateTime.plusMinutes(1), products = emptyList())
        carts.saveCart(edited)
        assertEquals(200, handler.handle(event()).statusCode)
        assertEquals(edited, carts.getCartBySessionId("first"))
    }
    @Test fun unpaidFailedExpiredAndInvalidEventsDoNotClearCarts() {
        saveOrder()
        assertEquals(400, handler.handle(event().copy(headers = emptyMap())).statusCode)
        for (type in listOf("checkout.session.completed", "checkout.session.async_payment_failed", "checkout.session.expired")) {
            assertEquals(200, handler.handle(event(type, false)).statusCode)
            assertEquals(first, carts.getCartBySessionId("first"))
        }
        assertEquals(200, handler.handle(event("checkout.session.async_payment_succeeded")).statusCode)
        assertNull(carts.getCartBySessionId("first"))
    }
    @Test fun legacySnapshotsClearOnlyMatchingContents() {
        saveOrder(legacy = true)
        val edited = first.copy(dateTime = first.dateTime.plusMinutes(1), products = emptyList())
        carts.saveCart(edited); cleaner.clear("checkout-first")
        assertEquals(edited, carts.getCartBySessionId("first"))
        carts.saveCart(first); cleaner.clear("checkout-first")
        assertNull(carts.getCartBySessionId("first"))
    }
}
