package dto.cart

import ShoppingCartProduct
import domain.cart.ShoppingCart
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingCartResponseTest {

    @Test
    fun `serializes sample identity and units`() {
        val cart =
            ShoppingCart(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                sessionId = "sample-session",
                dateTime = LocalDateTime.parse("2026-09-18T10:00:00"),
                products =
                    listOf(
                        ShoppingCartProduct(
                            productId = 9278L,
                            squareMeters = null,
                            amountBoxes = null,
                            totalPricePerProduct = BigDecimal("40.00"),
                            isSample = true,
                            sampleUnits = 2
                        )
                    )
            )

        val product =
            Json.parseToJsonElement(
                cart.toResponse().toJson()
            ).jsonObject["products"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?: error("Missing response product")

        assertEquals(true, product["isSample"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(2, product["sampleUnits"]?.jsonPrimitive?.content?.toInt())
    }
}
