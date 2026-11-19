package services.checkout.core

import ShoppingCartProduct
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import productdatabaseaccesslayer.ProductDataAccess
import services.cart.PurchasableProductReader

class CheckoutProductResolverSampleTest {

    @Test
    fun `resolves sample units into checkout quantity and total`() {
        val resolver =
            CheckoutProductResolver(
                PurchasableProductReader(
                    object : ProductDataAccess {
                        override fun getAllProducts(): String = "[]"

                        override fun getPurchasableProducts(): String = "[]"

                        override fun getProductBySlug(productSlug: String): String = "{}"

                        override fun getProductById(productId: Long): String =
                            """
                            {
                              "id": 9278,
                              "title": "Azure Tide MC52",
                              "purchase_information": {
                                "sample": {
                                  "sample_available": true,
                                  "sample_price": "20.00",
                                  "sample_max_quantity": "3"
                                },
                                "order": {
                                  "m2_per_box": "0.5",
                                  "client_price_per_m2": "180"
                                }
                              }
                            }
                            """.trimIndent()
                    }
                )
            )

        val lineItem =
            resolver.resolve(
                ShoppingCartProduct(
                    productId = 9278L,
                    squareMeters = null,
                    amountBoxes = null,
                    totalPricePerProduct = BigDecimal("40.00"),
                    isSample = true,
                    sampleUnits = 2
                )
            )

        assertEquals(2, lineItem.quantity)
        assertEquals(BigDecimal("40.00"), lineItem.amountTotal)
        assertEquals("Azure Tide MC52 sample", lineItem.name)
    }
}
