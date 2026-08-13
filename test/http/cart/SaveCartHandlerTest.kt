package http.cart

import db.offline.InMemoryShoppingCartRepository
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import services.DefaultShoppingCartService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class SaveCartHandlerTest {
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
    fun `creates a session cart with product quantity in m2`() {
        val handler = SaveCartHandler(
            DefaultShoppingCartService(InMemoryShoppingCartRepository(), productDataAccess, clock)
        )

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/cart",
                body = """
                    {
                      "sessionId": "session-123",
                      "products": [
                        { "productId": 9278, "quantityM2": 0.5 }
                      ]
                    }
                """.trimIndent()
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(201, response.statusCode)
        assertEquals("session-123", responseBody["sessionId"]?.jsonPrimitive?.content)
        assertEquals("2026-08-07T13:30", responseBody["dateTime"]?.jsonPrimitive?.content)
        assertEquals("9278", responseBody["products"]?.jsonArray?.get(0)?.jsonObject?.get("productId")?.jsonPrimitive?.content)
        assertEquals("0.5", responseBody["products"]?.jsonArray?.get(0)?.jsonObject?.get("squareMeters")?.jsonPrimitive?.content)
        assertEquals("1", responseBody["products"]?.jsonArray?.get(0)?.jsonObject?.get("amountBoxes")?.jsonPrimitive?.content)
        assertEquals("75.00", responseBody["products"]?.jsonArray?.get(0)?.jsonObject?.get("totalPricePerProduct")?.jsonPrimitive?.content)
    }

    @Test
    fun `returns bad request when required fields are missing`() {
        val handler = SaveCartHandler(
            DefaultShoppingCartService(InMemoryShoppingCartRepository(), productDataAccess, clock)
        )

        val response = handler.handle(HttpRequest(method = "POST", path = "/cart", body = "{}"))

        assertEquals(400, response.statusCode)
    }

    @Test
    fun `returns bad request when body is invalid json`() {
        val handler = SaveCartHandler(
            DefaultShoppingCartService(InMemoryShoppingCartRepository(), productDataAccess, clock)
        )

        val response = handler.handle(HttpRequest(method = "POST", path = "/cart", body = "{"))

        assertEquals(400, response.statusCode)
    }
}
