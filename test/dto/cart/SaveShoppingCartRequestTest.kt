package http

import dto.cart.toEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SaveShoppingCartRequestTest {
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

        assertEquals(9278L to 0.5, request.products[0].toEntity())
    }
}
