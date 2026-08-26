package http.checkout

import db.offline.InMemoryShoppingCartRepository
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import services.DefaultCheckoutService
import services.DefaultShoppingCartService
import services.StripePaymentProvider
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateCheckoutHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)
    private val productDataAccess = object : ProductDataAccess {
        override fun getProductById(productId: Long): String? =
            if (productId == 9278L) {
                """
                {
                  "id": 9278,
                  "title": "The Blues – Azure Tide MC52 – Handmade Glazed Ceramic Tile",
                  "image": "https://cms.deferranti.com/product-9278.png",
                  "purchase_information": {
                    "order": {
                      "m2_per_box": "0.5",
                      "client_price_per_m2": "150"
                    }
                  }
                }
                """.trimIndent()
            } else {
                null
            }
    }

    @Test
    fun `creates pending stripe checkout when api link is not configured yet`() {
        val shoppingCartService = DefaultShoppingCartService(
            InMemoryShoppingCartRepository(),
            productDataAccess,
            clock
        )
        val cart = shoppingCartService.createCart(
            sessionId = "session-123",
            products = listOf(9278L to 0.5),
            userId = null
        )
        val handler = CreateCheckoutHandler(
            DefaultCheckoutService(
                shoppingCartService,
                StripePaymentProvider(checkoutApiUrl = null)
            )
        )

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/checkout",
                body = """
                    {
                      "sessionId": "session-123",
                      "successUrl": "https://example.com/success",
                      "cancelUrl": "https://example.com/cancel",
                      "customerEmail": "buyer@example.com"
                    }
                """.trimIndent()
            )
        )
        val body = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(201, response.statusCode)
        assertEquals("stripe_pending_${cart.id}", body["id"]?.jsonPrimitive?.content)
        assertEquals("stripe", body["provider"]?.jsonPrimitive?.content)
        assertEquals("api_link_missing", body["status"]?.jsonPrimitive?.content)
        assertNull(body["url"])
    }

    @Test
    fun `creates stripe checkout session through configured api link`() {
        val capturedRequest = CapturedStripeRequest()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/v1/checkout/sessions") { exchange ->
                capturedRequest.authorization = exchange.requestHeaders.getFirst("Authorization")
                capturedRequest.contentType = exchange.requestHeaders.getFirst("Content-Type")
                capturedRequest.body = exchange.requestBody.bufferedReader().use { it.readText() }

                val responseBody = """
                    {
                      "id": "cs_test_123",
                      "status": "open",
                      "url": "https://checkout.stripe.com/c/pay/cs_test_123"
                    }
                """.trimIndent()

                exchange.sendResponseHeaders(200, responseBody.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(responseBody.toByteArray()) }
            }
            start()
        }

        try {
            val shoppingCartService = DefaultShoppingCartService(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )
            shoppingCartService.createCart(
                sessionId = "session-123",
                products = listOf(9278L to 0.5),
                userId = null
            )
            val handler = CreateCheckoutHandler(
                DefaultCheckoutService(
                    shoppingCartService,
                    StripePaymentProvider(
                        checkoutApiUrl = "http://localhost:${server.address.port}/v1/checkout/sessions",
                        secretKey = "sk_test_123"
                    ),
                    productDataAccess
                )
            )

            val response = handler.handle(
                HttpRequest(
                    method = "POST",
                    path = "/checkout",
                    body = """
                        {
                          "sessionId": "session-123",
                          "successUrl": "https://example.com/success",
                          "cancelUrl": "https://example.com/cancel",
                          "customerEmail": "buyer@example.com"
                        }
                    """.trimIndent()
                )
            )
            val body = Json.parseToJsonElement(response.body).jsonObject
            val form = capturedRequest.formValues()

            assertEquals(201, response.statusCode)
            assertEquals("cs_test_123", body["id"]?.jsonPrimitive?.content)
            assertEquals("stripe", body["provider"]?.jsonPrimitive?.content)
            assertEquals("open", body["status"]?.jsonPrimitive?.content)
            assertEquals("https://checkout.stripe.com/c/pay/cs_test_123", body["url"]?.jsonPrimitive?.content)
            assertEquals("Bearer sk_test_123", capturedRequest.authorization)
            assertEquals("application/x-www-form-urlencoded", capturedRequest.contentType)
            assertEquals("payment", form["mode"])
            assertEquals("en", form["locale"])
            assertEquals("card", form["payment_method_types[0]"])
            assertEquals("paypal", form["payment_method_types[1]"])
            assertEquals("link", form["payment_method_types[2]"])
            assertEquals("mb_way", form["payment_method_types[3]"])
            assertEquals("klarna", form["payment_method_types[4]"])
            assertEquals("https://example.com/success", form["success_url"])
            assertEquals("https://example.com/cancel", form["cancel_url"])
            assertEquals("buyer@example.com", form["customer_email"])
            assertEquals("1", form["line_items[0][quantity]"])
            assertEquals("eur", form["line_items[0][price_data][currency]"])
            assertEquals("7500", form["line_items[0][price_data][unit_amount]"])
            assertEquals(
                "The Blues – Azure Tide MC52 – Handmade Glazed Ceramic Tile",
                form["line_items[0][price_data][product_data][name]"]
            )
            assertEquals(
                "https://cms.deferranti.com/product-9278.png",
                form["line_items[0][price_data][product_data][images][0]"]
            )
            assertEquals("9278", form["line_items[0][price_data][product_data][metadata][product_id]"])
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `returns not found when session cart does not exist`() {
        val shoppingCartService = DefaultShoppingCartService(
            InMemoryShoppingCartRepository(),
            productDataAccess,
            clock
        )
        val handler = CreateCheckoutHandler(
            DefaultCheckoutService(
                shoppingCartService,
                StripePaymentProvider(checkoutApiUrl = null)
            )
        )

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/checkout",
                body = """
                    {
                      "sessionId": "missing-session",
                      "successUrl": "https://example.com/success",
                      "cancelUrl": "https://example.com/cancel"
                    }
                """.trimIndent()
            )
        )
        val body = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(404, response.statusCode)
        assertEquals("Shopping cart not found", body["message"]?.jsonPrimitive?.content)
        assertEquals("No shopping cart exists for sessionId missing-session", body["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `returns bad request when required fields are missing`() {
        val handler = CreateCheckoutHandler(
            DefaultCheckoutService(
                DefaultShoppingCartService(InMemoryShoppingCartRepository(), productDataAccess, clock),
                StripePaymentProvider(checkoutApiUrl = null)
            )
        )

        val response = handler.handle(HttpRequest(method = "POST", path = "/checkout", body = "{}"))

        assertEquals(400, response.statusCode)
    }

    private class CapturedStripeRequest {
        var authorization: String? = null
        var contentType: String? = null
        var body: String = ""

        fun formValues(): Map<String, String> =
            body.split("&")
                .filter { it.isNotBlank() }
                .associate { value ->
                    val parts = value.split("=", limit = 2)
                    decode(parts[0]) to decode(parts.getOrElse(1) { "" })
                }

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}
