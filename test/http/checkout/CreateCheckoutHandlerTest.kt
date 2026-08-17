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
}
