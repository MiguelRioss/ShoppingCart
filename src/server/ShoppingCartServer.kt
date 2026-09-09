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
import services.cart.ShoppingCartManager
import services.checkout.CheckoutManager
import services.payment.StripePaymentProvider
import services.user.UserManager

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
        val checkoutService = CheckoutManager(
            shoppingCartService = shoppingCartService,
            paymentProvider = StripePaymentProvider(),
            productDataAccess = productCatalogDataSource
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
            Route("POST", "/checkout", CreateCheckoutHandler(checkoutService))
        )
    }
}
