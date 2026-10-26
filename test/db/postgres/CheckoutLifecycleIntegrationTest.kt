package db.postgres

import com.sun.net.httpserver.HttpServer
import domain.user.User
import domain.checkout.CheckoutShippingCharge
import http.HttpRequest
import http.checkout.StripeWebhookHandler
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import productdatabaseaccesslayer.ProductDataAccess
import services.cart.*
import services.checkout.core.CheckoutManager
import services.checkout.payment.stripe.*
import services.shipping.ShippingChargeProvider
import kotlin.test.*

/** Real PostgreSQL and production Stripe adapter, with a local Stripe API fixture. No real charge. */
class CheckoutLifecycleIntegrationTest {
    @Test fun `two orders one user persist independently and signed webhooks finalize each once`() {
        assumeTrue(System.getenv("RUN_ORDER_DB_TEST") == "true")
        val database = requireNotNull(Database.fromEnvironment())
        PostgresSchema(database).migrate()
        val user = UUID.randomUUID()
        val prefix = "cs_lifecycle_${UUID.randomUUID()}"
        val requests = mutableListOf<Map<String,String>>()
        val stripe = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        stripe.createContext("/sessions") { exchange ->
            val form = exchange.requestBody.bufferedReader().readText().split('&').associate {
                val parts = it.split('=', limit = 2)
                URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts[1], Charsets.UTF_8)
            }
            requests.add(form)
            val id = "${prefix}_${requests.size}"
            val response = """{"id":"$id","status":"open","url":"https://checkout.stripe.com/c/pay/$id"}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.use { it.write(response) }
        }
        stripe.start()
        val users = PostgresUserRepository(database)
        val orders = PostgresOrderRepository(database)
        try {
            users.saveUser(User(user, "checkout-${user}@example.invalid", "test-only", LocalDateTime.now()))
            val products = object : ProductDataAccess {
                override fun getAllProducts() = "[]"
                override fun getPurchasableProducts() = "[]"
                override fun getProductBySlug(productSlug: String) = "{}"
                override fun getProductById(productId: Long) = """{"id":9278,"title":"Test tile","for_sale":true,"purchase_information":{"order":{"m2_per_box":"0.5","client_price_per_m2":"150"}}}"""
            }
            val carts = ShoppingCartManager(PostgresShoppingCartRepository(database), products)
            val manager = CheckoutManager(carts, StripePaymentProvider(StripeConfig("sk_test_fixture", "http://127.0.0.1:${stripe.address.port}/sessions")),
                object : ShippingChargeProvider {
                    override fun calculateShipping(cart: domain.cart.ShoppingCart, destination: domain.shipment.ShipmentAddress) =
                        CheckoutShippingCharge("TEST", "Test delivery", BigDecimal("10.00"), "eur", "unavailable")
                }, PurchasableProductReader(products), orders)
            val webhook = StripeWebhookHandler(orders, { "whsec_fixture" })
            var firstCartId: UUID? = null
            repeat(2) { index ->
                val session = "$prefix-browser-$index"
                val cart = carts.createCart(session, listOf(CartProductInput(9278, 0.5 + index, false)), user)
                if (index == 0) firstCartId = cart.id else assertEquals(firstCartId, cart.id)
                val checkout = manager.createCheckoutSession(domain.checkout.CreateCheckoutSessionRequest(
                    session, "http://127.0.0.1:5500/checkout-success.html", "http://127.0.0.1:5500/checkout-error.html?status=cancel",
                    "test@example.invalid", CheckoutShippingCharge("TEST", "Test delivery", BigDecimal("10.00"), "eur", "unavailable"), userId = user))
                assertTrue(checkout.url!!.startsWith("https://checkout.stripe.com/"))
                assertEquals("http://127.0.0.1:5500/checkout-success.html", requests.last()["success_url"])
                assertEquals("http://127.0.0.1:5500/checkout-error.html?status=cancel", requests.last()["cancel_url"])
                val current = orders.list(user).first { it.getValue("checkoutSessionId").jsonPrimitive.content == checkout.id }
                assertEquals("PENDING_PAYMENT", current.getValue("status").jsonPrimitive.content)
                val amount = if (index == 0) 8500 else 23500
                val body = """{"id":"evt_$index","type":"checkout.session.completed","data":{"object":{"id":"${checkout.id}","payment_status":"paid","amount_total":$amount,"currency":"eur","payment_intent":"pi_$index"}}}"""
                val time = Instant.now().epochSecond
                val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec("whsec_fixture".toByteArray(), "HmacSHA256"))
                val signature = mac.doFinal("$time.$body".toByteArray()).joinToString("") { "%02x".format(it) }
                repeat(2) { assertEquals(200, webhook.handle(HttpRequest("POST", "/webhooks/stripe", body, mapOf("Stripe-Signature" to listOf("t=$time,v1=$signature")))).statusCode) }
            }
            val saved = PostgresOrderRepository(database).list(user)
            assertEquals(2, saved.size)
            assertTrue(saved.all { it.getValue("status").jsonPrimitive.content == "PAID" })
            assertEquals(setOf("85.00", "235.00"), saved.map { it.getValue("snapshot").jsonObject.getValue("total").jsonPrimitive.content }.toSet())
            assertTrue(orders.list(UUID.randomUUID()).isEmpty())
            // Repository-level concurrency: two new cart IDs cannot violate the one-cart-per-user constraint.
            val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val saves = (1..2).map { n -> executor.submit<domain.cart.ShoppingCart> {
                    PostgresShoppingCartRepository(database).saveCart(domain.cart.ShoppingCart(
                        UUID.randomUUID(), user, LocalDateTime.now(), "$prefix-concurrent-$n"))
                } }
                assertEquals(1, saves.map { it.get().id }.distinct().size)
            } finally { executor.shutdownNow() }
        } finally {
            stripe.stop(0)
            database.getConnection().use { c ->
                c.prepareStatement("DELETE FROM orders WHERE user_id = ?").use { it.setObject(1,user); it.executeUpdate() }
                c.prepareStatement("DELETE FROM users WHERE id = ?").use { it.setObject(1,user); it.executeUpdate() }
            }
            database.close()
        }
    }
}
