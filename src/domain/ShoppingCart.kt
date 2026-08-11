package domain

import java.time.LocalDateTime
import java.util.UUID

data class ShoppingCart(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val products: List<ShoppingCartProduct> = emptyList()
)
