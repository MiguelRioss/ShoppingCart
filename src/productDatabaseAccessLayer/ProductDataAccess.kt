package productdatabaseaccesslayer

/**
 * Product-catalog lookup boundary used by cart logic.
 */
fun interface ProductDataAccess {
    /**
     * Checks whether a product id exists in the catalog.
     *
     * @param productId external catalog product id
     * @return true when the product exists
     */
    fun productExists(productId: Long): Boolean
}
