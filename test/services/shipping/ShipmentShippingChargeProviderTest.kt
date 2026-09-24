package services.shipping

import ShoppingCartProduct
import domain.cart.ShoppingCart
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import productdatabaseaccesslayer.ProductDataAccess
import shipment.core.ProviderAccount
import shipment.core.ProviderAuthToken
import shipment.core.ProviderCredentials
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentPackage
import domain.shipment.Shipment
import domain.shipment.ShipmentRequest
import domain.shipment.ShipmentRateRequest
import shipment.core.ShipmentProviderService
import shipment.core.ShipmentProviderType
import domain.shipment.ShipmentQuote

class ShipmentShippingChargeProviderTest {

    @Test
    fun `calculateShipping sums cheapest quote from each supplier origin`() {
        val shipmentProvider =
            RecordingShipmentProviderService(
                quotes =
                    listOf(
                        ShipmentQuote(
                            ShipmentProviderType.FEDEX,
                            "FEDEX_PRIORITY",
                            "FedEx Priority",
                            40.0,
                            "EUR",
                            "2026-09-18"
                        ),
                        ShipmentQuote(
                            ShipmentProviderType.FEDEX,
                            "FEDEX_ECONOMY",
                            "FedEx Economy",
                            25.0,
                            "EUR",
                            "2026-09-21"
                        )
                    )
            )

        val productDataAccess =
            object : ProductDataAccess {
                override fun getAllProducts(): String = "[]"
                override fun getPurchasableProducts(): String = "[]"
                override fun getProductBySlug(productSlug: String): String = "{}"

                override fun getProductById(productId: Long): String? =
                    getProductDetailsById(productId)

                override fun getProductDetailsById(productId: Long): String =
                    when (productId) {
                        9278L -> productJson(productId)
                        9279L ->
                            productJson(
                                productId = productId,
                                address = "Rua de Santa Catarina 100",
                                city = "Porto",
                                postalCode = "4000-442"
                            )
                        else -> error("Unexpected product $productId")
                    }
            }

        val charge =
            ShipmentShippingChargeProvider(
                shipmentProvider = shipmentProvider,
                productDataAccess = productDataAccess
            ).calculateShipping(
                cart =
                    ShoppingCart(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        userId = null,
                        dateTime = LocalDateTime.parse("2026-09-16T10:00:00"),
                        sessionId = "multi-origin-session",
                        products =
                            listOf(
                                ShoppingCartProduct(
                                    productId = 9278L,
                                    squareMeters = 0.5,
                                    amountBoxes = 1,
                                    totalPricePerProduct = BigDecimal("90.00"),
                                    isSample = false
                                ),
                                ShoppingCartProduct(
                                    productId = 9279L,
                                    squareMeters = 0.5,
                                    amountBoxes = 1,
                                    totalPricePerProduct = BigDecimal("75.00"),
                                    isSample = false
                                )
                            )
                    ),
                destination =
                    ShipmentAddress(
                        addressLine1 = "1 Piccadilly",
                        city = "Manchester",
                        postalCode = "M1 1AE",
                        countryCode = "GB"
                    )
            )

        assertEquals(2, shipmentProvider.requests.size)
        assertEquals(1, shipmentProvider.requests[0].packages.size)
        assertEquals(1, shipmentProvider.requests[1].packages.size)
        assertEquals(
            setOf("Albergaria dos Doze", "Porto"),
            shipmentProvider.requests.map { it.from.city }.toSet()
        )

        requireNotNull(charge)
        assertEquals("MULTI_ORIGIN", charge.serviceType)
        assertEquals("Shipping (2 origins)", charge.serviceName)
        assertEquals(BigDecimal("50.0"), charge.amountTotal)
        assertEquals("eur", charge.currency)
        assertEquals("2026-09-21", charge.estimatedDeliveryDate)
    }

    @Test
    fun `calculateShipping builds shipment request from product data and returns cheapest quote`() {
        val shipmentProvider =
            RecordingShipmentProviderService(
                quotes =
                    listOf(
                        ShipmentQuote(
                            ShipmentProviderType.FEDEX,
                            "FEDEX_GBP",
                            "FedEx GBP",
                            10.0,
                            "GBP",
                            "2026-09-17"
                        ),
                        ShipmentQuote(
                            ShipmentProviderType.FEDEX,
                            "FEDEX_PRIORITY",
                            "FedEx Priority",
                            40.0,
                            "EUR",
                            "2026-09-18"
                        ),
                        ShipmentQuote(
                            ShipmentProviderType.FEDEX,
                            "FEDEX_ECONOMY",
                            "FedEx Economy",
                            25.0,
                            "EUR",
                            "2026-09-21"
                        )
                    )
            )

        val provider =
            ShipmentShippingChargeProvider(
                shipmentProvider = shipmentProvider,
                productDataAccess =
                    object : ProductDataAccess {
                        override fun getAllProducts(): String {
                            TODO("Not yet implemented")
                        }

                        override fun getPurchasableProducts(): String {
                            TODO("Not yet implemented")
                        }

                        override fun getProductById(productId: Long): String? =
                            productJson(productId)

                        override fun getProductBySlug(productSlug: String): String {
                            TODO("Not yet implemented")
                        }

                        override fun getProductDetailsById(productId: Long): String? =
                            productJson(productId)
                    }
            )

        val destination =
            ShipmentAddress(
                addressLine1 = "Rua do Destinatario 1",
                city = "Faro",
                postalCode = "8000-339",
                countryCode = "PT"
            )

        val charge =
            provider.calculateShipping(
                cart =
                    ShoppingCart(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        userId = null,
                        dateTime = LocalDateTime.parse("2026-09-16T10:00:00"),
                        sessionId = "session-123",
                        products =
                            listOf(
                                ShoppingCartProduct(
                                    productId = 9278L,
                                    squareMeters = 0.5,
                                    amountBoxes = 1,
                                    totalPricePerProduct = BigDecimal("90.00"),
                                    isSample = false
                                ),
                                ShoppingCartProduct(
                                    productId = 9278L,
                                    squareMeters = null,
                                    amountBoxes = null,
                                    totalPricePerProduct = BigDecimal("20.00"),
                                    isSample = true,
                                    sampleUnits = 1
                                )
                            )
                    ),
                destination = destination
            )

        assertEquals(
            ShipmentAddress(
                addressLine1 = "Rua da Vidoeira 1",
                city = "Albergaria dos Doze",
                postalCode = "3100-097",
                countryCode = "PT"
            ),
            shipmentProvider.lastFrom
        )

        assertEquals(
            destination,
            shipmentProvider.lastTo
        )

        val shipmentPackages =
            requireNotNull(shipmentProvider.lastProducts)
        assertEquals(2, shipmentPackages.size)

        val shipmentPackage =
            shipmentPackages[0]

        assertEquals("Handmade glazed ceramic tiles", shipmentPackage.description)
        assertEquals("PT", shipmentPackage.countryOfManufacture)
        assertEquals(1, shipmentPackage.quantity)
        assertEquals("PCS", shipmentPackage.quantityUnits)
        assertEquals(8.2, shipmentPackage.weight.value)
        assertEquals("KG", shipmentPackage.weight.units)
        assertEquals(30, shipmentPackage.dimensions.length)
        assertEquals(30, shipmentPackage.dimensions.width)
        assertEquals(20, shipmentPackage.dimensions.height)
        assertEquals("CM", shipmentPackage.dimensions.units)
        assertEquals(90.0, shipmentPackage.customsValue)
        assertEquals("EUR", shipmentPackage.customsCurrency)
        assertEquals("EUR", shipmentProvider.lastRequest?.preferredCurrency)

        val samplePackage =
            shipmentPackages[1]
        assertEquals("Azure Tide sample", samplePackage.description)
        assertEquals(1, samplePackage.quantity)
        assertEquals(0.2, samplePackage.weight.value)
        assertEquals(11, samplePackage.dimensions.length)
        assertEquals(11, samplePackage.dimensions.width)
        assertEquals(1, samplePackage.dimensions.height)
        assertEquals(20.0, samplePackage.customsValue)

        requireNotNull(charge)
        assertEquals("FEDEX_ECONOMY", charge.serviceType)
        assertEquals("FedEx Economy", charge.serviceName)
        assertEquals(BigDecimal("25.0"), charge.amountTotal)
        assertEquals("eur", charge.currency)
        assertEquals("2026-09-21", charge.estimatedDeliveryDate)
        assertEquals(BigDecimal("25.0"), charge.originalAmount)
        assertEquals("EUR", charge.originalCurrency)
    }

    private fun productJson(
        productId: Long,
        address: String = "Rua da Vidoeira 1",
        city: String = "Albergaria dos Doze",
        postalCode: String = "3100-097"
    ): String =
        """
        {
          "id": $productId,
          "title": "Azure Tide sample",
          "supplier": {
            "address_2": "$address",
            "city_collection": "$city",
            "post_code_collection": "$postalCode",
            "country_collection": "Portugal"
          },
          "purchase_information": {
            "sample": {
              "sample_available": true,
              "sample_length": "11",
              "sample_width": "11",
              "sample_thickness": "0.9",
              "sample_packed_weight": "200"
            },
            "order": {
              "product_dispatch_source": "De Ferranti Portugal Warehouse"
            },
            "shipping": {
              "packing_size": [
                {
                  "quantity": "0.5",
                  "packed_weight": "8.2",
                  "length": "30",
                  "width": "30",
                  "height": "20"
                }
              ]
            },
            "tax_freight_and_customs": {
              "customs_description": "Handmade glazed ceramic tiles",
              "country_of_origin": "Portugal"
            }
          }
        }
        """.trimIndent()

    private class RecordingShipmentProviderService(
        private val quotes: List<ShipmentQuote>
    ) : ShipmentProviderService {

        override val type: ShipmentProviderType =
            ShipmentProviderType.FEDEX

        override val account: ProviderAccount =
            ProviderAccount("test-account", countryCode = "UK")

        override val credentials: ProviderCredentials =
            ProviderCredentials("test-client", "test-secret")

        val requests = mutableListOf<ShipmentRateRequest>()
        val lastRequest: ShipmentRateRequest? get() = requests.lastOrNull()
        val lastFrom: ShipmentAddress? get() = lastRequest?.from
        val lastTo: ShipmentAddress? get() = lastRequest?.to
        val lastProducts: List<ShipmentPackage>? get() = lastRequest?.packages

        override fun login(): ProviderAuthToken =
            ProviderAuthToken("test-token")

        override fun createShipment(request: ShipmentRequest): Shipment =
            error("Shipment creation is not used by this rate test")

        override fun getQuotes(request: ShipmentRateRequest): List<ShipmentQuote> {
            requests += request
            return quotes
        }
    }
}
