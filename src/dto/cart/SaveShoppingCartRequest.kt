package dto.cart

data class SaveShoppingCartRequest(
    val sessionId: String?,
    val products: List<SaveShoppingCartProductRequest>
)

data class SaveShoppingCartProductRequest(
    val productId: Long?,
    val quantityM2: Double?
)

fun SaveShoppingCartProductRequest.toEntity(): Pair<Long, Double> =
    requireNotNull(productId) to requireNotNull(quantityM2)
