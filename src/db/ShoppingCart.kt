package db

import java.util.UUID

data class ShoppingCart(
    val id: UUID,
    val sessionId: UUID,
    val items: List<CartItem> = emptyList()
)
