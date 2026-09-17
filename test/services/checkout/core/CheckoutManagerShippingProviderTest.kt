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
    import shipment.core.ShipmentAddress

    class CheckoutManagerShippingProviderTest {

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

        private class FixedShippingChargeProvider(
            private val charge: CheckoutShippingCharge
        ) : ShippingChargeProvider {

            override fun calculateShipping(
                cart: ShoppingCart,
                destination: ShipmentAddress
            ): CheckoutShippingCharge =
                charge
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
