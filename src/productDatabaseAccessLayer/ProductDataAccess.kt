package productdatabaseaccesslayer

fun interface ProductDataAccess {
    fun productExists(productId: Long): Boolean
}
