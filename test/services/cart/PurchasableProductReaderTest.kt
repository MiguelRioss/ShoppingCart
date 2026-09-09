package services.cart

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import productdatabaseaccesslayer.ProductDataAccess
import services.common.ServiceErrorCode
import services.common.ServiceException

class PurchasableProductReaderTest {
    @Test
    fun `loads purchasable product values from purchase order`() {
        val reader = PurchasableProductReader(
            productDataAccess = productDataAccess(
                """
                {
                  "id": 9278,
                  "purchase_information": {
                    "order": {
                      "m2_per_box": "0.5",
                      "client_price_per_m2": "150"
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val product = reader.load(9278L)

        assertEquals(BigDecimal("0.5"), product.m2PerBox)
        assertEquals(BigDecimal("150"), product.pricePerM2)
    }

    @Test
    fun `returns clear error when purchase information is missing`() {
        val reader = PurchasableProductReader(
            productDataAccess = productDataAccess(
                """
                {
                  "id": 1864,
                  "purchase_information": null
                }
                """.trimIndent()
            )
        )

        val error = assertFailsWith<ServiceException> {
            reader.load(1864L)
        }

        assertEquals(ServiceErrorCode.ProductPurchaseInformationMissing, error.errorCode)
        assertEquals("Product 1864 purchase information is missing", error.description)
    }

    @Test
    fun `returns clear error when order information is missing`() {
        val reader = PurchasableProductReader(
            productDataAccess = productDataAccess(
                """
                {
                  "id": 1864,
                  "purchase_information": {
                    "order": null
                  }
                }
                """.trimIndent()
            )
        )

        val error = assertFailsWith<ServiceException> {
            reader.load(1864L)
        }

        assertEquals(ServiceErrorCode.ProductOrderInformationMissing, error.errorCode)
        assertEquals("Product 1864 order information is missing", error.description)
    }

    private fun productDataAccess(productJson: String): ProductDataAccess =
        object : ProductDataAccess {
            override fun getProductById(productId: Long): String = productJson
        }
}

