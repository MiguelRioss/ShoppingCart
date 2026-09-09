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
     * Finds a detailed product record by external catalog id.
     *
     * The detailed endpoint may include heavier product metadata such as shipping,
     * customs, and packing rows that is intentionally omitted from list endpoints.
     */
    fun getProductDetailsById(productId: Long): String? = getProductById(productId)

    /**
     * Checks whether a product id exists in the catalog.
     *
     * @param productId external catalog product id
     * @return true when the product exists
     */
    fun productExists(productId: Long): Boolean = getProductById(productId) != null
}

