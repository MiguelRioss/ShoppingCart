    package services.checkout.core

    import db.offline.InMemoryShoppingCartRepository
    import domain.cart.ShoppingCart
    import domain.checkout.CheckoutSession
    import domain.checkout.CheckoutSessionStatus
    import domain.checkout.CheckoutShippingCharge
    import domain.checkout.CreateCheckoutSessionRequest
    import domain.checkout.payment.CheckoutPaymentRequest
    import domain.checkout.payment.PaymentEvent
    import domain.checkout.payment.PaymentRefund
    import domain.checkout.payment.PaymentWebhookRequest
    import domain.checkout.payment.RefundPaymentRequest
    import java.math.BigDecimal
    import kotlin.test.Test
    import kotlin.test.assertEquals
    import kotlin.test.assertFailsWith
    import kotlin.test.assertTrue
    import productdatabaseaccesslayer.ProductDataAccess
    import services.cart.CartProductInput
    import services.cart.PurchasableProductReader
    import services.cart.ShoppingCartManager
    import services.checkout.payment.core.PaymentProviderInterface
    import services.checkout.payment.core.PaymentProviderType
    import services.shipping.ShippingChargeProvider
    import services.shipping.ShipmentShippingChargeProvider
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

    class CheckoutManagerShippingProviderTest {

        @Test
        fun `checkout sends products sample and shipping to payment provider in euros`() {
            val productDataAccess =
                shippingProductDataAccess()
            val shoppingCartService =
                ShoppingCartManager(
                    shoppingCartRepository = InMemoryShoppingCartRepository(),
                    productDataAccess = productDataAccess
                )

            shoppingCartService.createCart(
                sessionId = "mixed-currency-session",
                products =
                    listOf(
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = 0.5,
                            isSample = false
                        ),
                        CartProductInput(
                            productId = 9278L,
                            quantityM2 = null,
                            isSample = true,
                            sampleUnits = 1
                        )
                    ),
                userId = null
            )

            val paymentProvider =
                RecordingPaymentProvider()
            val checkoutManager =
                CheckoutManager(
                    shoppingCartService = shoppingCartService,
                    paymentProvider = paymentProvider,
                    shippingChargeProvider =
                        ShipmentShippingChargeProvider(
                            shipmentProvider =
                                FixedShipmentProviderService(
                                    listOf(
                                        ShipmentQuote(
                                            ShipmentProviderType.FEDEX,
                                            "FEDEX_GBP",
                                            "FedEx GBP",
                                            10.0,
                                            "GBP",
                                            "2026-09-22"
                                        ),
                                        ShipmentQuote(
                                            ShipmentProviderType.FEDEX,
                                            "FEDEX_EUR",
                                            "FedEx EUR",
                                            25.0,
                                            "EUR",
                                            "2026-09-23"
                                        )
                                    )
                                ),
                            productDataAccess = productDataAccess
                        ),
                    purchasableProductReader =
                        PurchasableProductReader(productDataAccess)
                )

            checkoutManager.createCheckoutSession(
                CreateCheckoutSessionRequest(
                    sessionId = "mixed-currency-session",
                    successUrl = "https://example.com/success",
                    cancelUrl = "https://example.com/cancel",
                    customerEmail = "buyer@example.com",
                    shippingCharge = null,
                    deliveryAddress =
                        domain.checkout.CheckoutDeliveryAddress(
                            company = null,
                            addressLine1 = "10 Delivery Street",
                            addressLine2 = null,
                            townOrCity = "Lisbon",
                            postcode = "1000-001",
                            country = "PT"
                        )
                )
            )

            val lineItems =
                requireNotNull(paymentProvider.lastRequest).lineItems

            assertEquals(3, lineItems.size)
            assertTrue(
                lineItems.all {
                    it.currency == "eur"
                }
            )

            val shippingLine =
                lineItems.single {
                    it.productId == 0L
                }

            assertEquals("FedEx EUR", shippingLine.name)
            assertEquals(BigDecimal("25.0"), shippingLine.amountTotal)
        }

        @Test
        fun `createCheckoutSession uses shipping provider when request has no shipping charge`() {
            val productDataAccess =
                checkoutProductDataAccess()

            val shoppingCartService =
                ShoppingCartManager(
                    shoppingCartRepository = InMemoryShoppingCartRepository(),
                    productDataAccess = productDataAccess
                )

            shoppingCartService.createCart(
                sessionId = "session-123",
                products = listOf(CartProductInput(
                    productId = 9278L,
                    quantityM2 = 0.5,
                    isSample = false
                )),
                userId = null
            )

            val paymentProvider =
                RecordingPaymentProvider()

            val checkoutManager =
                CheckoutManager(
                    shoppingCartService = shoppingCartService,
                    paymentProvider = paymentProvider,
                    shippingChargeProvider =
                        FixedShippingChargeProvider(
                            CheckoutShippingCharge(
                                serviceType = "FEDEX_PRIORITY",
                                serviceName = "FedEx Priority",
                                amountTotal = BigDecimal("25.00"),
                                currency = "eur",
                                estimatedDeliveryDate = "2026-09-15",
                                originalAmount = BigDecimal("25.00"),
                                originalCurrency = "EUR"
                            )
                        ),
                    purchasableProductReader = PurchasableProductReader(productDataAccess)
                )

            checkoutManager.createCheckoutSession(
                CreateCheckoutSessionRequest(
                    sessionId = "session-123",
                    successUrl = "https://example.com/success",
                    cancelUrl = "https://example.com/cancel",
                    customerEmail = "buyer@example.com",
                    shippingCharge = null,
                    deliveryAddress =
                        domain.checkout.CheckoutDeliveryAddress(
                            company = null,
                            addressLine1 = "10 Delivery Street",
                            addressLine2 = null,
                            townOrCity = "Lisbon",
                            postcode = "1000-001",
                            country = "PT"
                        )
                )
            )

            val request =
                requireNotNull(paymentProvider.lastRequest)

            assertEquals(2, request.lineItems.size)
            assertTrue(request.lineItems.any { it.productId == 9278L })

            val shippingLine =
                request.lineItems.single { it.productId == 0L }

            assertEquals("FedEx Priority", shippingLine.name)
            assertEquals(BigDecimal("25.00"), shippingLine.amountTotal)
            assertEquals("eur", shippingLine.currency)
        }

        @Test
        fun `createCheckoutSession fails when shipping provider needs delivery address and none is supplied`() {
            val productDataAccess =
                checkoutProductDataAccess()

            val shoppingCartService =
                ShoppingCartManager(
                    shoppingCartRepository = InMemoryShoppingCartRepository(),
                    productDataAccess = productDataAccess
                )

            shoppingCartService.createCart(
                sessionId = "session-123",
                products = listOf(CartProductInput(
                    productId = 9278L,
                    quantityM2 = 0.5,
                    isSample = false
                )),
                userId = null
            )

            val checkoutManager =
                CheckoutManager(
                    shoppingCartService = shoppingCartService,
                    paymentProvider = RecordingPaymentProvider(),
                    shippingChargeProvider =
                        FixedShippingChargeProvider(
                            CheckoutShippingCharge(
                                serviceType = "FEDEX_PRIORITY",
                                serviceName = "FedEx Priority",
                                amountTotal = BigDecimal("25.00"),
                                currency = "eur"
                            )
                        ),
                    purchasableProductReader = PurchasableProductReader(productDataAccess)
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    checkoutManager.createCheckoutSession(
                        CreateCheckoutSessionRequest(
                            sessionId = "session-123",
                            successUrl = "https://example.com/success",
                            cancelUrl = "https://example.com/cancel",
                            customerEmail = "buyer@example.com",
                            shippingCharge = null,
                            deliveryAddress = null
                        )
                    )
                }

            assertEquals(
                "deliveryAddress is required when shippingCharge is not supplied",
                error.message
            )
        }

        @Test
        fun `createCheckoutSession fails when shipping charge is missing and shipping provider is not configured`() {
            val productDataAccess =
                checkoutProductDataAccess()

            val shoppingCartService =
                ShoppingCartManager(
                    shoppingCartRepository = InMemoryShoppingCartRepository(),
                    productDataAccess = productDataAccess
                )

            shoppingCartService.createCart(
                sessionId = "session-123",
                products = listOf(CartProductInput(
                    productId = 9278L,
                    quantityM2 = 0.5,
                    isSample = false
                )),
                userId = null
            )

            val checkoutManager =
                CheckoutManager(
                    shoppingCartService = shoppingCartService,
                    paymentProvider = RecordingPaymentProvider(),
                    shippingChargeProvider = null,
                    purchasableProductReader = PurchasableProductReader(productDataAccess)
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    checkoutManager.createCheckoutSession(
                        CreateCheckoutSessionRequest(
                            sessionId = "session-123",
                            successUrl = "https://example.com/success",
                            cancelUrl = "https://example.com/cancel",
                            customerEmail = "buyer@example.com",
                            shippingCharge = null,
                            deliveryAddress =
                                domain.checkout.CheckoutDeliveryAddress(
                                    company = null,
                                    addressLine1 = "10 Delivery Street",
                                    addressLine2 = null,
                                    townOrCity = "Lisbon",
                                    postcode = "1000-001",
                                    country = "PT"
                                )
                        )
                    )
                }

            assertEquals(
                "shippingChargeProvider is required when shippingCharge is not supplied",
                error.message
            )
        }

        private fun checkoutProductDataAccess(): ProductDataAccess =
            object : ProductDataAccess {
                override fun getAllProducts(): String {
                    TODO("Not yet implemented")
                }

                override fun getPurchasableProducts(): String {
                    TODO("Not yet implemented")
                }

                override fun getProductById(productId: Long): String =
                    """
                    {
                      "id": $productId,
                      "title": "Azure Tide MC52",
                      "image": "https://example.com/tile.png",
                      "for_sale": true,
                      "purchase_information": {
                        "order": {
                          "m2_per_box": "0.5",
                          "client_price_per_m2": "150"
                        }
                      }
                    }
                    """.trimIndent()

                override fun getProductBySlug(productSlug: String): String {
                    TODO("Not yet implemented")
                }
            }

        private fun shippingProductDataAccess(): ProductDataAccess =
            object : ProductDataAccess {
                override fun getAllProducts(): String = "[]"

                override fun getPurchasableProducts(): String = "[]"

                override fun getProductBySlug(productSlug: String): String = "{}"

                override fun getProductById(productId: Long): String =
                    productJson(productId)

                override fun getProductDetailsById(productId: Long): String =
                    productJson(productId)

                private fun productJson(productId: Long): String =
                    """
                    {
                      "id": $productId,
                      "title": "Azure Tide MC52",
                      "image": "https://example.com/tile.png",
                      "supplier": {
                        "address_2": "Rua da Vidoeira 1",
                        "city_collection": "Albergaria dos Doze",
                        "post_code_collection": "3100-097",
                        "country_collection": "Portugal"
                      },
                      "purchase_information": {
                        "sample": {
                          "sample_available": true,
                          "sample_price": "20.00",
                          "sample_max_quantity": "3",
                          "sample_length": "11",
                          "sample_width": "11",
                          "sample_thickness": "0.9",
                          "sample_packed_weight": "200"
                        },
                        "order": {
                          "m2_per_box": "0.5",
                          "client_price_per_m2": "150"
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
                          "customs_description": "Handmade ceramic tiles",
                          "country_of_origin": "Portugal"
                        }
                      }
                    }
                    """.trimIndent()
            }

        private class FixedShippingChargeProvider(
            private val charge: CheckoutShippingCharge
        ) : ShippingChargeProvider {

            override fun calculateShipping(
                cart: ShoppingCart,
                destination: ShipmentAddress
            ): CheckoutShippingCharge =
                charge
        }

        private class FixedShipmentProviderService(
            private val quotes: List<ShipmentQuote>
        ) : ShipmentProviderService {
            override val type: ShipmentProviderType =
                ShipmentProviderType.FEDEX

            override val account: ProviderAccount =
                ProviderAccount("test-account", "PT")

            override val credentials: ProviderCredentials =
                ProviderCredentials("test-client", "test-secret")

            override fun login(): ProviderAuthToken =
                ProviderAuthToken("test-token")

            override fun createShipment(request: ShipmentRequest): Shipment =
                error("Shipment creation is not used by this checkout test")

            override fun getQuotes(request: ShipmentRateRequest): List<ShipmentQuote> =
                quotes
        }

        private class RecordingPaymentProvider : PaymentProviderInterface {

            override val type: PaymentProviderType =
                PaymentProviderType.STRIPE

            var lastRequest: CheckoutPaymentRequest? = null

            override fun createCheckoutSession(
                request: CheckoutPaymentRequest
            ): CheckoutSession {
                lastRequest = request

                return CheckoutSession(
                    id = "cs_test_123",
                    provider = type,
                    status = CheckoutSessionStatus.CREATED,
                    url = "https://checkout.example/cs_test_123"
                )
            }

            override fun getCheckoutSession(
                checkoutSessionId: String
            ): CheckoutSession =
                error("Not used in this test")

            override fun capturePayment(
                paymentId: String
            ): CheckoutSession =
                error("Not used in this test")

            override fun refundPayment(
                request: RefundPaymentRequest
            ): PaymentRefund =
                error("Not used in this test")

            override fun parseWebhook(
                request: PaymentWebhookRequest
            ): PaymentEvent =
                error("Not used in this test")
        }
    }
