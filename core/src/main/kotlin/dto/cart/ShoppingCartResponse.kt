package dto.cart

import ShoppingCartProduct
import domain.cart.ShoppingCart
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Public HTTP response returned for cart read and save operations.
 *
 * @property id persistent cart id
 * @property userId authenticated user id when the cart is attached to a user
 * @property dateTime last cart write time
 * @property sessionId browser or application session id associated with the cart
 * @property products calculated cart product lines
 */
data class ShoppingCartResponse(
    val id: UUID,
    val userId: UUID?,
    val dateTime: LocalDateTime,
    val sessionId: String?,
    val products: List<ShoppingCartProduct>
)

/**
 * Maps the cart domain model into its HTTP response DTO.
 */
fun ShoppingCart.toResponse(): ShoppingCartResponse =
    ShoppingCartResponse(
        id = id,
        userId = userId,
        dateTime = dateTime,
        sessionId = sessionId,
        products = products
    )

/**
 * Serializes the cart response as compact JSON.
 */
fun ShoppingCartResponse.toJson(): String =
    buildJsonObject {

        put(
            "id",
            JsonPrimitive(id.toString())
        )

        userId?.let {
            put(
                "userId",
                JsonPrimitive(it.toString())
            )
        }

        sessionId?.let {
            put(
                "sessionId",
                JsonPrimitive(it)
            )
        }

        put(
            "dateTime",
            JsonPrimitive(dateTime.toString())
        )

        put(
            "products",
            buildJsonArray {
                products.forEach { product ->
                    add(
                        product.toJsonObject()
                    )
                }
            }
        )
    }.toString()

private fun ShoppingCartProduct.toJsonObject(): JsonObject =
    buildJsonObject {

        put(
            "productId",
            JsonPrimitive(productId)
        )

        put(
            "squareMeters",
            JsonPrimitive(squareMeters)
        )

        put(
            "amountBoxes",
            JsonPrimitive(amountBoxes)
        )

        put(
            "totalPricePerProduct",
            JsonPrimitive(
                totalPricePerProduct.toPlainString()
            )
        )

        put(
            "isSample",
            JsonPrimitive(isSample)
        )

        sampleUnits?.let {
            put(
                "sampleUnits",
                JsonPrimitive(it)
            )
        }
    }
