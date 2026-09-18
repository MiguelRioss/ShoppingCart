package http

import dto.cart.parseSaveShoppingCartRequest
import dto.cart.toEntity
import org.junit.jupiter.api.Test
import services.cart.CartProductInput
import kotlin.test.assertEquals

class SaveShoppingCartRequestTest {

    @Test
    fun `parses sample units`() {
        val request = parseSaveShoppingCartRequest(
            """
            {
              "sessionId": "sample-session",
              "products": [
                { "productId": 9278, "isSample": true, "sampleUnits": 2 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(true, request.products.single().isSample)
        assertEquals(2, request.products.single().sampleUnits)
    }
    @Test
    fun `parses save cart request`() {
        val request = parseSaveShoppingCartRequest(
            """
            {
              "sessionId": "session-123",
              "products": [
                { "productId": 9278, "quantityM2": 0.5 }
              ]
            }
            """.trimIndent()
        )

        assertEquals("session-123", request.sessionId)
        assertEquals(9278L, request.products[0].productId)
        assertEquals(0.5, request.products[0].quantityM2)
    }

    @Test
    fun `maps product request to service entity input`() {
        val request = parseSaveShoppingCartRequest(
            """
        {
          "sessionId": "session-123",
          "products": [
            { "productId": 9278, "quantityM2": 0.5 }
          ]
        }
        """.trimIndent()
        )

        assertEquals(
            CartProductInput(
                productId = 9278L,
                quantityM2 = 0.5,
                isSample = false
            ),
            request.products[0].toEntity()
        )
    }
}
