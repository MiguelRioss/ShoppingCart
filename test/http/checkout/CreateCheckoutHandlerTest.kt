package http.checkout

import com.sun.net.httpserver.HttpServer
import db.offline.InMemoryShoppingCartRepository
import http.HttpRequest
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import services.cart.CartProductInput
import services.cart.PurchasableProductReader
import services.cart.ShoppingCartManager
import services.cart.ShoppingCartService
import services.checkout.core.CheckoutManager
import services.checkout.payment.stripe.StripeConfig
import services.checkout.payment.stripe.StripePaymentProvider
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateCheckoutHandlerTest {

    private val users = db.offline.InMemoryUserRepository()
    private val authService by lazy {
        val user = services.user.UserManager(users, clock = clock)
            .registerUser("buyer@example.com", "password-123")
        users.saveUser(user.copy(
            deliveryAddressLine1 = "Account street",
            deliveryTownOrCity = "Lisbon",
            deliveryPostcode = "1000-001",
            deliveryCountry = "PT"
        ))
        services.auth.AuthManager(users, db.offline.InMemoryAuthTokenRepository(), clock = clock)
    }
    private val authHeaders by lazy {
        val token = authService.login("buyer@example.com", "password-123")
        mapOf("Authorization" to listOf("Bearer ${token.token}"))
    }

    private val clock =
        Clock.fixed(
            Instant.parse("2026-08-07T13:30:00Z"),
            ZoneOffset.UTC
        )

    private val productDataAccess =
        object : ProductDataAccess {
            override fun getAllProducts(): String {
                TODO("Not yet implemented")
            }

            override fun getPurchasableProducts(): String {
                TODO("Not yet implemented")
            }

            override fun getProductById(
                productId: Long
            ): String? =
                if (productId == 9278L) {
                    """
                    {
                      "id": 9278,
                      "title": "The Blues – Azure Tide MC52 – Handmade Glazed Ceramic Tile",
                      "image": "https://cms.deferranti.com/product-9278.png",
                      "for_sale": true,
                      "purchase_information": {
                        "order": {
                          "m2_per_box": "0.5",
                          "client_price_per_m2": "150"
                        }
                      }
                    }
                    """.trimIndent()
                } else {
                    null
                }

            override fun getProductBySlug(productSlug: String): String {
                TODO("Not yet implemented")
            }

            override fun getProductDetailsById(
                productId: Long
            ): String? =
                getProductById(
                    productId
                )
        }

    private val purchasableProductReader =
        PurchasableProductReader(
            productDataAccess
        )

    @Test
    fun `creates stripe checkout session through configured api`() {

        val capturedRequest =
            CapturedStripeRequest()

        val server =
            HttpServer.create(
                InetSocketAddress(0),
                0
            ).apply {

                createContext(
                    "/v1/checkout/sessions"
                ) { exchange ->

                    capturedRequest.authorization =
                        exchange.requestHeaders.getFirst(
                            "Authorization"
                        )

                    capturedRequest.contentType =
                        exchange.requestHeaders.getFirst(
                            "Content-Type"
                        )

                    capturedRequest.body =
                        exchange.requestBody
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    val responseBody =
                        """
                        {
                          "id": "cs_test_123",
                          "status": "open",
                          "url": "https://checkout.stripe.com/c/pay/cs_test_123"
                        }
                        """.trimIndent()

                    exchange.sendResponseHeaders(
                        200,
                        responseBody
                            .toByteArray()
                            .size
                            .toLong()
                    )

                    exchange.responseBody.use {
                        it.write(
                            responseBody.toByteArray()
                        )
                    }
                }

                start()
            }

        try {

            val shoppingCartService =
                createShoppingCartService()

            shoppingCartService.createCart(
                sessionId = "session-123",
                products =
                    listOf(CartProductInput(
                        productId = 9278L,
                        quantityM2 = 0.5,
                        isSample = false
                    )),
                userId = null
            )

            val stripePaymentProvider =
                StripePaymentProvider(
                    config =
                        StripeConfig(
                            secretKey = "sk_test_123",
                            checkoutApiUrl =
                                "http://127.0.0.1:${server.address.port}/v1/checkout/sessions"
                        )
                )

            val handler =
                CreateCheckoutHandler(
                    CheckoutManager(
                        shoppingCartService =
                            shoppingCartService,

                        paymentProvider =
                            stripePaymentProvider,

                        shippingChargeProvider = object : services.shipping.ShippingChargeProvider {
                            override fun calculateShipping(
                                cart: domain.cart.ShoppingCart,
                                destination: domain.shipment.ShipmentAddress
                            ): domain.checkout.CheckoutShippingCharge {
                                assertEquals("Account street", destination.addressLine1)
                                return domain.checkout.CheckoutShippingCharge(
                                    serviceType = "STANDARD_DELIVERY",
                                    serviceName = "Standard delivery",
                                    amountTotal = java.math.BigDecimal("408.96"),
                                    currency = "eur",
                                    estimatedDeliveryDate = "unavailable",
                                    originalAmount = java.math.BigDecimal("408.96"),
                                    originalCurrency = "USD"
                                )
                            }
                        },

                        purchasableProductReader =
                            purchasableProductReader
                    ),
                    authService
                )

            val response =
                handler.handle(
                    HttpRequest(
                        method = "POST",
                        path = "/checkout",
                        headers = authHeaders,
                        body =
                            """
                            {
                              "sessionId": "session-123",
                              "successUrl": "https://example.com/success",
                              "cancelUrl": "https://example.com/cancel"
                            }
                            """.trimIndent()
                    )
                )

            val body =
                Json.parseToJsonElement(
                    response.body
                ).jsonObject

            val form =
                capturedRequest.formValues()

            assertEquals(
                201,
                response.statusCode
            )

            assertEquals(
                "cs_test_123",
                body["id"]
                    ?.jsonPrimitive
                    ?.content
            )

            assertEquals(
                "stripe",
                body["provider"]
                    ?.jsonPrimitive
                    ?.content
            )

            assertEquals(
                "created",
                body["status"]
                    ?.jsonPrimitive
                    ?.content
            )

            assertEquals(
                "https://checkout.stripe.com/c/pay/cs_test_123",
                body["url"]
                    ?.jsonPrimitive
                    ?.content
            )

            assertEquals(
                "Bearer sk_test_123",
                capturedRequest.authorization
            )

            assertEquals(
                "application/x-www-form-urlencoded",
                capturedRequest.contentType
            )

            assertEquals(
                "payment",
                form["mode"]
            )

            assertEquals(
                "en",
                form["locale"]
            )

            assertEquals(
                "card",
                form["payment_method_types[0]"]
            )

            assertEquals(
                "paypal",
                form["payment_method_types[1]"]
            )

            assertEquals(
                "link",
                form["payment_method_types[2]"]
            )

            assertEquals(
                "mb_way",
                form["payment_method_types[3]"]
            )

            assertEquals(
                "klarna",
                form["payment_method_types[4]"]
            )

            assertEquals(
                "https://example.com/success",
                form["success_url"]
            )

            assertEquals(
                "https://example.com/cancel",
                form["cancel_url"]
            )

            assertEquals(
                "buyer@example.com",
                form["customer_email"]
            )

            assertEquals(
                "1",
                form["line_items[0][quantity]"]
            )

            assertEquals(
                "eur",
                form[
                    "line_items[0][price_data][currency]"
                ]
            )

            assertEquals(
                "7500",
                form[
                    "line_items[0][price_data][unit_amount]"
                ]
            )

            assertEquals(
                "The Blues – Azure Tide MC52 – Handmade Glazed Ceramic Tile",
                form[
                    "line_items[0][price_data][product_data][name]"
                ]
            )

            assertEquals(
                "https://cms.deferranti.com/product-9278.png",
                form[
                    "line_items[0][price_data][product_data][images][0]"
                ]
            )

            assertEquals(
                "9278",
                form[
                    "line_items[0][price_data][product_data][metadata][product_id]"
                ]
            )

            assertEquals(
                "1",
                form["line_items[1][quantity]"]
            )

            assertEquals(
                "eur",
                form[
                    "line_items[1][price_data][currency]"
                ]
            )

            assertEquals(
                "40896",
                form[
                    "line_items[1][price_data][unit_amount]"
                ]
            )

            assertEquals(
                "Standard delivery",
                form[
                    "line_items[1][price_data][product_data][name]"
                ]
            )

            assertNull(
                form[
                    "line_items[1][price_data][product_data][description]"
                ]
            )

            assertEquals(
                "0",
                form[
                    "line_items[1][price_data][product_data][metadata][product_id]"
                ]
            )

        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `returns not found when session cart does not exist`() {

        val shoppingCartService =
            createShoppingCartService()

        val handler =
            CreateCheckoutHandler(
                createCheckoutManager(
                    shoppingCartService
                ),
                authService
            )

        val response =
            handler.handle(
                HttpRequest(
                    method = "POST",
                    path = "/checkout",
                    headers = authHeaders,
                    body =
                        """
                        {
                          "sessionId": "missing-session",
                          "successUrl": "https://example.com/success",
                          "cancelUrl": "https://example.com/cancel"
                        }
                        """.trimIndent()
                )
            )

        val body =
            Json.parseToJsonElement(
                response.body
            ).jsonObject

        assertEquals(
            404,
            response.statusCode
        )

        assertEquals(
            "Shopping cart not found",
            body["message"]
                ?.jsonPrimitive
                ?.content
        )

        assertEquals(
            "No shopping cart exists for sessionId missing-session",
            body["description"]
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun `returns bad request when checkout square meters are not a multiple of half`() {

        val shoppingCartService =
            createShoppingCartService()

        shoppingCartService.createCart(
            sessionId = "session-123",
            products =
                listOf(
                    CartProductInput(
                        productId = 9278L,
                        quantityM2 = 1.25,
                        isSample = false
                    )
                ),
            userId = null
        )

        val handler =
            CreateCheckoutHandler(
                createCheckoutManager(
                    shoppingCartService
                ),
                authService
            )

        val response =
            handler.handle(
                HttpRequest(
                    method = "POST",
                    path = "/checkout",
                    headers = authHeaders,
                    body =
                        """
                        {
                          "sessionId": "session-123",
                          "successUrl": "https://example.com/success",
                          "cancelUrl": "https://example.com/cancel"
                        }
                        """.trimIndent()
                )
            )

        val body =
            Json.parseToJsonElement(
                response.body
            ).jsonObject

        assertEquals(
            400,
            response.statusCode
        )

        assertEquals(
            "Invalid product quantity",
            body["message"]
                ?.jsonPrimitive
                ?.content
        )

        assertEquals(
            "Product 9278 has 1.25 m2, " +
                    "but checkout quantities must be multiples of 0.5 m2",
            body["description"]
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun `returns bad request when required fields are missing`() {

        val shoppingCartService =
            createShoppingCartService()

        val handler =
            CreateCheckoutHandler(
                createCheckoutManager(
                    shoppingCartService
                ),
                authService
            )

        val response =
            handler.handle(
                HttpRequest(
                    method = "POST",
                    path = "/checkout",
                    headers = authHeaders,
                    body = "{}"
                )
            )

        assertEquals(
            400,
            response.statusCode
        )
    }

    private fun createShoppingCartService(): ShoppingCartManager =
        ShoppingCartManager(
            InMemoryShoppingCartRepository(),
            productDataAccess,
            clock
        )

    @Test
    fun `checkout requires a valid bearer token before parsing payload`() {
        val handler = CreateCheckoutHandler(createCheckoutManager(createShoppingCartService()), authService)
        for (headers in listOf(emptyMap(), mapOf("Authorization" to listOf("Bearer invalid")))) {
            val response = handler.handle(HttpRequest("POST", "/checkout", "{}", headers = headers))
            assertEquals(401, response.statusCode)
        }
    }

    @Test
    fun `checkout rejects delivery address and shipping charge in payload`() {
        val handler = CreateCheckoutHandler(createCheckoutManager(createShoppingCartService()), authService)
        for (field in listOf("deliveryAddress", "shippingCharge")) {
            val response = handler.handle(HttpRequest(
                "POST", "/checkout",
                """{"sessionId":"session-123","successUrl":"https://example.com/success","cancelUrl":"https://example.com/cancel","$field":{}}""",
                headers = authHeaders
            ))
            assertEquals(400, response.statusCode)
        }
    }

    @Test
    fun `checkout rejects carts belonging to another user`() {
        val carts = createShoppingCartService()
        carts.createCart(
            sessionId = "another-user-session",
            products = listOf(CartProductInput(9278L, 0.5, false)),
            userId = java.util.UUID.randomUUID()
        )
        val handler = CreateCheckoutHandler(createCheckoutManager(carts), authService)
        val response = handler.handle(HttpRequest(
            "POST", "/checkout",
            """{"sessionId":"another-user-session","successUrl":"https://example.com/success","cancelUrl":"https://example.com/cancel"}""",
            headers = authHeaders
        ))
        assertEquals(400, response.statusCode)
    }

    @Test
    fun `checkout requires complete delivery address saved on account`() {
        val headers = authHeaders
        val user = users.getUserByEmail("buyer@example.com")!!
        users.saveUser(user.copy(deliveryPostcode = null))
        val carts = createShoppingCartService()
        carts.createCart("session-123", listOf(CartProductInput(9278L, 0.5, false)), userId = user.id)
        val handler = CreateCheckoutHandler(createCheckoutManager(carts), authService)
        val response = handler.handle(HttpRequest(
            "POST", "/checkout",
            """{"sessionId":"session-123","successUrl":"https://example.com/success","cancelUrl":"https://example.com/cancel"}""",
            headers = headers
        ))
        assertEquals(400, response.statusCode)
    }

    private fun createCheckoutManager(
        shoppingCartService: ShoppingCartService
    ): CheckoutManager =
        CheckoutManager(
            shoppingCartService =
                shoppingCartService,

            paymentProvider =
                StripePaymentProvider(
                    config =
                        StripeConfig(
                            secretKey =
                                "sk_test_not_used",

                            checkoutApiUrl =
                                "http://127.0.0.1:1/v1/checkout/sessions"
                        )
                ),

            purchasableProductReader =
                purchasableProductReader
        )

    private class CapturedStripeRequest {

        var authorization: String? = null

        var contentType: String? = null

        var body: String = ""

        fun formValues(): Map<String, String> =
            body
                .split("&")
                .filter {
                    it.isNotBlank()
                }
                .associate { value ->

                    val parts =
                        value.split(
                            "=",
                            limit = 2
                        )

                    decode(
                        parts[0]
                    ) to
                            decode(
                                parts.getOrElse(1) {
                                    ""
                                }
                            )
                }

        private fun decode(
            value: String
        ): String =
            URLDecoder.decode(
                value,
                StandardCharsets.UTF_8
            )
    }
}
