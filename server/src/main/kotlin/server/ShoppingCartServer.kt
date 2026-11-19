package server

import PaymentProviderResolver
import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryShoppingCartRepository
import db.offline.InMemoryUserRepository
import db.postgres.Database
import db.postgres.PostgresAuthTokenRepository
import db.postgres.PostgresSchema
import db.postgres.PostgresShoppingCartRepository
import db.postgres.PostgresUserRepository
import http.HttpModule
import http.Route
import http.Server
import http.auth.AuthStatusHandler
import http.auth.DeleteAccountHandler
import http.auth.EditAccountHandler
import http.auth.LoginHandler
import http.auth.RegisterHandler
import http.cart.ClearCartHandler
import http.cart.GetCartHandler
import http.cart.SaveCartHandler
import http.checkout.CreateCheckoutHandler
import http.client.GetClientInfoHandler
import http.product.GetProductByIdHandler
import http.product.GetProductsHandler
import http.shipping.PrepareShippingGoodsHandler
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.auth.AuthManager
import services.auth.LoginService
import services.cart.PurchasableProductReader
import services.cart.ShoppingCartManager
import services.checkout.core.CheckoutManager
import services.checkout.payment.stripe.StripePaymentProvider
import services.shipping.rates.ShipmentShippingChargeProvider
import services.shipping.rates.FedExDutyTaxEstimator
import services.shipping.fulfillment.PaidOrderShipmentBookingService
import services.shipping.fulfillment.ShippingGoodsPreparationService
import services.user.UserManager
import config.ShippingMode
import config.ShippingProviderConfig
import java.util.logging.Logger
import db.offline.InMemoryOrderRepository
import db.postgres.PostgresOrderRepository
import services.checkout.payment.stripe.StripeConfig
import shipment.core.ShippingProvider

/**
 * Builds repositories, services, routes, and the HTTP server.
 */
class ShoppingCartServer(
    private val database: Database?,
    private val port: Int
) {
    private val logger = Logger.getLogger(javaClass.name)

    fun start() {
        server().start()
        logger.info("ShoppingCart server listening on port $port")
    }

    private fun server(): Server =
        Server(
            httpModule(),
            port
        )

    private fun httpModule(): HttpModule =
        HttpModule(
            routes = routes()
        )

    private fun routes(): List<Route> {
        val userRepository = database?.let {
            PostgresSchema(it).migrate()
            PostgresUserRepository(it)
        } ?: InMemoryUserRepository()
        val authTokenRepository = database?.let { PostgresAuthTokenRepository(it) } ?: InMemoryAuthTokenRepository()
        val shoppingCartRepository = database?.let { PostgresShoppingCartRepository(it) } ?: InMemoryShoppingCartRepository()
        val authService = AuthManager(userRepository, authTokenRepository)
        val userService = UserManager(userRepository)
        val productCatalogDataSource = ProductCatalogDataSource()
        val shoppingCartService = ShoppingCartManager(shoppingCartRepository, productCatalogDataSource)
        val loginService = LoginService(authService, shoppingCartService)
        val orderRepository: db.OrderRepository = database?.let { PostgresOrderRepository(it) } ?: InMemoryOrderRepository()
        val shippingMode = ShippingProviderConfig.mode()
        check(shippingMode != ShippingMode.OFFLINE) {
            "FEDEX_MODE must be SANDBOX or PRODUCTION; OFFLINE is not supported by the running server"
        }
        val carrier = shippingProvider(shippingMode)
        val shippingChargeProvider = ShipmentShippingChargeProvider(
            shipmentProvider = carrier,
            productDataAccess = productCatalogDataSource,
            dutyTaxEstimator = FedExDutyTaxEstimator()
        )

        val portugueseStripe = StripePaymentProvider(
            config = StripeConfig.fromEnvironment("PT")
        )

        val britishStripe = StripePaymentProvider(
            config = StripeConfig.fromEnvironment("UK")
        )

// Select the appropriate Stripe account
        val paymentProviderResolver = PaymentProviderResolver(
            portugueseStripe = portugueseStripe,
            britishStripe = britishStripe
        )

        val checkoutService = CheckoutManager(
            shoppingCartService = shoppingCartService,
            paymentProviderResolver = paymentProviderResolver,
            shippingChargeProvider = shippingChargeProvider,
            purchasableProductReader = PurchasableProductReader(
                productDataAccess = productCatalogDataSource
            ),
            orderService = services.order.OrderManager(orderRepository, productCatalogDataSource)
        )
        val adminUserIds = config.Environment.get("ADMIN_USER_IDS").orEmpty()
            .split(',').mapNotNull { runCatching { java.util.UUID.fromString(it.trim()) }.getOrNull() }.toSet()

        val orderManager = services.order.OrderManager(orderRepository, productCatalogDataSource)
        val emailSenderEndpointAdapter = services.orderemail.EmailSenderEndpointAdapter()
        val emailSenderApi = services.orderemail.HttpEmailSenderApi(
            baseUrl = emailSenderEndpointAdapter::baseUrl
        )
        val paidOrderEmailMapper = services.orderemail.PaidOrderEmailPayloadMapper(orderRepository, orderManager)
        val purchaseCompleteEmailSender = services.orderemail.PurchaseCompleteEmailSender(
            paidOrderEmailMapper,
            emailSenderApi
        )
        val shippingGoodsEmailSender = services.orderemail.ShippingGoodsEmailSender(
            paidOrderEmailMapper,
            emailSenderApi
        )

        val paidOrderShipmentBooking = PaidOrderShipmentBookingService(
            orderRepository,
            services.shipping.ProductShipmentRequestMapper(productCatalogDataSource, validateGrossVsCalculatedNet = true),
            carrier
        )
        val shippingGoodsPreparation = ShippingGoodsPreparationService(
            orderRepository,
            paidOrderShipmentBooking,
            shippingGoodsEmailSender
        )
        return listOf(
            Route("POST", "/system/shipping-goods", PrepareShippingGoodsHandler(shippingGoodsPreparation)),
            Route("POST", "/admin/orders/:orderId/prepare-shipping",
                http.admin.PrepareOrderShippingHandler(authService, shippingGoodsPreparation, adminUserIds)),
            Route("GET", "/orders", http.checkout.GetOrdersHandler(orderRepository, authService)),
            Route("POST", "/webhooks/stripe", http.checkout.StripeWebhookHandler(orderRepository,
                clearPaidCart = services.checkout.core.PaidOrderCartCleaner(orderRepository, shoppingCartRepository)::clear,
                onPaymentConfirmed = { checkoutId ->
                    purchaseCompleteEmailSender.onPaid(checkoutId)
                })),
            //To investigate better
            Route("GET", "/admin/orders", http.admin.GetOrdersHandler(authService, orderRepository, adminUserIds)),
            Route("GET", "/admin/users", http.admin.GetUsersHandler(authService, userRepository, adminUserIds)),
            Route("POST", "/register", RegisterHandler(userService, authService, shoppingCartService)),
            Route("POST", "/login", LoginHandler(loginService)),
            Route("GET", "/auth/status", AuthStatusHandler(authService)),
            Route("PUT", "/account", EditAccountHandler(authService, userService)),
            Route("DELETE", "/account", DeleteAccountHandler(authService, userService)),
            Route("GET", "/client/info", GetClientInfoHandler(authService)),
            Route("GET", "/products", GetProductsHandler(productCatalogDataSource)),
            Route("GET", "/products/:id", GetProductByIdHandler(productCatalogDataSource)),
            Route("GET", "/cart", GetCartHandler(authService, shoppingCartService)),
            Route("POST", "/cart", SaveCartHandler(shoppingCartService, authService)),
            Route("POST", "/cart/clear", ClearCartHandler(shoppingCartService)),
            Route("POST", "/checkout", CreateCheckoutHandler(checkoutService, authService))
        )
    }

    /** Creates the cart shipping service using the selected carrier implementation. */
    private fun shippingProvider(mode: ShippingMode): ShippingProvider {
        return when (mode) {
            ShippingMode.OFFLINE -> error("FEDEX_MODE must be SANDBOX or PRODUCTION")
            ShippingMode.SANDBOX, ShippingMode.PRODUCTION -> services.shipping.AccountRoutingShippingProvider()
        }
    }
}
