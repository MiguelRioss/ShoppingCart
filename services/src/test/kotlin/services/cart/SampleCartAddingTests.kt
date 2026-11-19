package services.cart

import db.offline.InMemoryShoppingCartRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import services.common.ServiceErrorCode
import services.common.ServiceException

class SampleCartAddingTests {

    private val clock =
        Clock.fixed(
            Instant.parse("2026-08-07T13:30:00Z"),
            ZoneOffset.UTC
        )

    private val productDataAccess =
        productDataAccess(
            sampleAvailable = true,
            samplePrice = "20.00"
        )

    @Test
    fun `adds sample to cart without square meters`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val cart =
            service.createCart(
                sessionId = "sample-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )

        assertEquals(
            1,
            cart.products.size
        )

        val sample =
            cart.products.single()

        assertEquals(
            9278L,
            sample.productId
        )

        assertTrue(
            sample.isSample
        )

        assertNull(
            sample.squareMeters
        )

        assertNull(
            sample.amountBoxes
        )

        assertEquals(
            1,
            sample.sampleUnits
        )

        assertEquals(
            BigDecimal("20.00"),
            sample.totalPricePerProduct
        )
    }

    @Test
    fun `sample cart is saved in repository`() {
        val repository =
            InMemoryShoppingCartRepository()

        val service =
            ShoppingCartManager(
                repository,
                productDataAccess,
                clock
            )

        val cart =
            service.createCart(
                sessionId = "sample-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )

        val savedCart =
            repository.getCartBySessionId(
                "sample-session"
            )

        assertEquals(
            cart,
            savedCart
        )

        val savedSample =
            requireNotNull(savedCart)
                .products
                .single()

        assertTrue(
            savedSample.isSample
        )

        assertNull(
            savedSample.squareMeters
        )

        assertNull(
            savedSample.amountBoxes
        )

        assertEquals(
            BigDecimal("20.00"),
            savedSample.totalPricePerProduct
        )
    }

    @Test
    fun `sample and normal product can exist in same cart`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val cart =
            service.createCart(
                sessionId = "mixed-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        ),
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = 0.5,
                            isSample = false
                        )
                    ),
                userId = null
            )

        assertEquals(
            2,
            cart.products.size
        )

        val sample =
            cart.products.first {
                it.isSample
            }

        val normalProduct =
            cart.products.first {
                !it.isSample
            }

        assertEquals(
            9278L,
            sample.productId
        )

        assertNull(
            sample.squareMeters
        )

        assertNull(
            sample.amountBoxes
        )

        assertEquals(
            BigDecimal("20.00"),
            sample.totalPricePerProduct
        )

        assertEquals(
            9278L,
            normalProduct.productId
        )

        assertEquals(
            0.5,
            normalProduct.squareMeters
        )

        assertEquals(
            1,
            normalProduct.amountBoxes
        )

        assertEquals(
            BigDecimal("90.00"),
            normalProduct.totalPricePerProduct
        )
    }

    @Test
    fun `sample uses sample price instead of normal product price`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val cart =
            service.createCart(
                sessionId = "sample-price-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )

        val sample =
            cart.products.single()

        assertEquals(
            BigDecimal("20.00"),
            sample.totalPricePerProduct
        )

        assertFalse(
            sample.totalPricePerProduct ==
                    BigDecimal("90.00")
        )
    }

    @Test
    fun `sample price is multiplied by sample units`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val sample =
            service.createCart(
                sessionId = "multiple-samples-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 2
                        )
                    ),
                userId = null
            ).products.single()

        assertEquals(2, sample.sampleUnits)
        assertEquals(BigDecimal("40.00"), sample.totalPricePerProduct)
    }

    @Test
    fun `sample units cannot exceed catalog maximum`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val exception =
            assertFailsWith<ServiceException> {
                service.createCart(
                    sessionId = "too-many-samples-session",
                    products =
                        listOf(
                            CartProductInput(
                                productId = 9278L,
                                quantityM2 = null,
                                isSample = true,
                                sampleUnits = 4
                            )
                        ),
                    userId = null
                )
            }

        assertEquals(
            ServiceErrorCode.SampleMaximumQuantityExceeded,
            exception.errorCode
        )
    }

    @Test
    fun `sample requires positive sample units and no square meters`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        listOf(
            CartProductInput(9278L, null, true, null),
            CartProductInput(9278L, null, true, 0),
            CartProductInput(9278L, 0.5, true, 1)
        ).forEach { input ->
            val exception =
                assertFailsWith<ServiceException> {
                    service.createCart(
                        sessionId = "invalid-sample-session",
                        products = listOf(input),
                        userId = null
                    )
                }

            assertEquals(
                ServiceErrorCode.SampleUnitsInvalid,
                exception.errorCode
            )
        }
    }

    @Test
    fun `normal product calculation still works`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val cart =
            service.createCart(
                sessionId = "normal-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = 0.5,
                            isSample = false
                        )
                    ),
                userId = null
            )

        val product =
            cart.products.single()

        assertFalse(
            product.isSample
        )

        assertEquals(
            0.5,
            product.squareMeters
        )

        assertEquals(
            1,
            product.amountBoxes
        )

        assertEquals(
            BigDecimal("90.00"),
            product.totalPricePerProduct
        )
    }

    @Test
    fun `sample ignores square meter calculation`() {
        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                productDataAccess,
                clock
            )

        val cart =
            service.createCart(
                sessionId = "sample-no-m2-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )

        val sample =
            cart.products.single()

        assertNull(
            sample.squareMeters
        )

        assertNull(
            sample.amountBoxes
        )

        assertEquals(
            BigDecimal("20.00"),
            sample.totalPricePerProduct
        )
    }

    @Test
    fun `sample cannot be added when sample is unavailable`() {
        val unavailableProductDataAccess =
            productDataAccess(
                sampleAvailable = false,
                samplePrice = "20.00"
            )

        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                unavailableProductDataAccess,
                clock
            )

        val exception =
            assertFailsWith<ServiceException> {
                service.createCart(
                    sessionId = "unavailable-sample-session",
                    products =
                        listOf(
                            CartProductInput(
                                productId = 9278L,
                                quantityM2 = null,
                                isSample = true,
                                sampleUnits = 1
                            )
                        ),
                    userId = null
                )
            }

        assertEquals(
            ServiceErrorCode.ProductSampleUnavailable,
            exception.errorCode
        )
    }

    @Test
    fun `sample cannot be added when sample price is missing`() {
        val missingPriceProductDataAccess =
            productDataAccess(
                sampleAvailable = true,
                samplePrice = null
            )

        val service =
            ShoppingCartManager(
                InMemoryShoppingCartRepository(),
                missingPriceProductDataAccess,
                clock
            )

        assertFailsWith<IllegalArgumentException> {
            service.createCart(
                sessionId = "missing-price-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )
        }
    }

    @Test
    fun `updating existing cart can replace normal product with sample`() {
        val repository =
            InMemoryShoppingCartRepository()

        val service =
            ShoppingCartManager(
                repository,
                productDataAccess,
                clock
            )

        val originalCart =
            service.createCart(
                sessionId = "update-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = 0.5,
                            isSample = false
                        )
                    ),
                userId = null
            )

        val updatedCart =
            service.createCart(
                sessionId = "update-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )

        assertEquals(
            originalCart.id,
            updatedCart.id
        )

        val sample =
            updatedCart.products.single()

        assertTrue(
            sample.isSample
        )

        assertNull(
            sample.squareMeters
        )

        assertNull(
            sample.amountBoxes
        )

        assertEquals(
            BigDecimal("20.00"),
            sample.totalPricePerProduct
        )

        assertEquals(
            updatedCart,
            repository.getCartBySessionId(
                "update-session"
            )
        )
    }

    private fun productDataAccess(
        sampleAvailable: Boolean,
        samplePrice: String?
    ): ProductDataAccess =
        object : ProductDataAccess {

            override fun getAllProducts(): String =
                "[]"

            override fun getPurchasableProducts(): String =
                "[]"

            override fun getProductBySlug(
                productSlug: String
            ): String =
                "{}"

            override fun getProductById(
                productId: Long
            ): String? =
                if (productId == 9278L) {
                    productJson(
                        sampleAvailable,
                        samplePrice
                    )
                } else {
                    null
                }

            override fun getProductDetailsById(
                productId: Long
            ): String? =
                getProductById(
                    productId
                )
        }

    private fun productJson(
        sampleAvailable: Boolean,
        samplePrice: String?
    ): String {
        val samplePriceJson =
            samplePrice
                ?.let {
                    """"sample_price": "$it","""
                }
                ?: ""

        return """
            {
              "id": 9278,
              "title": "The Blues – Azure Tide MC52 – Handmade Glazed Ceramic Tile",
              "purchase_information": {
                "sample": {
                  "sample_available": $sampleAvailable,
                  $samplePriceJson
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
}
