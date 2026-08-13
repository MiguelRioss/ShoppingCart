package dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveShoppingCartRequestTest {
    @Test
    fun `parses save cart request`() {
        val request = SaveShoppingCartRequest.fromJson(
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
        assertTrue(request.isValid)
    }

    @Test
    fun `marks request invalid when required fields are missing`() {
        val request = SaveShoppingCartRequest.fromJson("{}")

        assertFalse(request.isValid)
    }
}
