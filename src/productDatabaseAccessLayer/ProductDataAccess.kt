package productdatabaseaccesslayer

/**
 * Product-catalog lookup boundary used by cart logic.
 */
interface ProductDataAccess {
    /**
     * Finds a product by external catalog id.
     *
     * @param productId external catalog product id
     * @return raw product JSON, or null when the product does not exist
     */
    fun getProductById(productId: Long): String?

    /**
     * Checks whether a product id exists in the catalog.
     *
     * @param productId external catalog product id
     * @return true when the product exists
     */
    fun productExists(productId: Long): Boolean = getProductById(productId) != null
}
