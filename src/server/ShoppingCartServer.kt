package server

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
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.auth.AuthManager
import services.auth.LoginService
import services.cart.PurchasableProductReader
import services.cart.ShoppingCartManager
import services.checkout.core.CheckoutManager
import services.checkout.payment.stripe.StripePaymentProvider
import services.shipping.ShipmentShippingChargeProvider
import services.user.UserManager
import config.ShippingMode
import config.ShippingProviderConfig
import shipment.core.ShippingProvider
import shipment.core.ShipmentProviderType

/**
 * Builds repositories, services, routes, and the HTTP server.
 */
class ShoppingCartServer(
    private val database: Database?,
    private val port: Int
) {
    fun start() {
        server().start()
        println("ShoppingCart server listening on http://localhost:$port")
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
        val shippingChargeProvider =
            shippingChargeProvider(productCatalogDataSource)
        val checkoutService = CheckoutManager(
            shoppingCartService = shoppingCartService,
            paymentProvider = StripePaymentProvider(),
            shippingChargeProvider = shippingChargeProvider,
            purchasableProductReader = PurchasableProductReader(productDataAccess = productCatalogDataSource)
        )

        return listOf(
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
    private fun shippingChargeProvider(
        productCatalogDataSource: ProductCatalogDataSource
    ): ShipmentShippingChargeProvider {
        val config = ShippingProviderConfig.load()
        val shippingProvider =
            when (config.mode) {
                ShippingMode.OFFLINE -> ShippingProvider.offline()
                ShippingMode.SANDBOX,
                ShippingMode.PRODUCTION ->
                    ShippingProvider.login(
                        type = ShipmentProviderType.FEDEX,
                        account = requireNotNull(config.account),
                        credentials = requireNotNull(config.credentials),
                        environment = requireNotNull(config.providerEnvironment)
                    )
            }

        return ShipmentShippingChargeProvider(
            shipmentProvider = shippingProvider,
            productDataAccess = productCatalogDataSource
        )
    }
}
