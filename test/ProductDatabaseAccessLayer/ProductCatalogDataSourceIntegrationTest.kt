package productdatabaseaccesslayer

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ProductCatalogDataSourceIntegrationTest {
    @Test
    fun `gets all products from the catalog`() {
        val response = ProductCatalogDataSource().getAllProducts()

        assertTrue(response.isNotBlank())
        assertTrue(response.contains("\"slug\""))
        assertTrue(response.contains("\"title\""))

        println("Products-all response sample:")
        println(response.take(1_000))
    }

    @Test
    fun `gets a product by slug`() {
        val response = ProductCatalogDataSource().getProductBySlug("alabaster-brick")

        assertTrue(response.isNotBlank())
        assertTrue(response.contains("\"slug\":\"alabaster-brick\""))
        assertTrue(response.contains("\"title\":\"Alabaster Brick\""))

        println("Product-by-slug response:")
        println(response.take(1_000))
    }
}
