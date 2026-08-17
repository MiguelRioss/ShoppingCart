import config.AppMode
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
import services.DefaultAuthService
import services.DefaultCheckoutService
import services.DefaultShoppingCartService
import services.DefaultUserService
import services.LoginService
import services.StripePaymentProvider

/**
 * Application entry point.
 *
 * Builds the in-memory repositories, services, route table, and HTTP server.
 * Render injects the PORT environment variable, while local runs default to 8080.
 */
fun main() {
    val database = if (AppMode.fromEnvironment() == AppMode.Online) {
        requireNotNull(Database.fromEnvironment()) { "DATABASE_URL is required when APP_MODE=online" }
    } else {
        null
    }
    val userRepository = database?.let {
        PostgresSchema(it).migrate()
        PostgresUserRepository(it)
    } ?: InMemoryUserRepository()
    val authTokenRepository = database?.let { PostgresAuthTokenRepository(it) } ?: InMemoryAuthTokenRepository()
    val shoppingCartRepository = database?.let { PostgresShoppingCartRepository(it) } ?: InMemoryShoppingCartRepository()
    val authService = DefaultAuthService(userRepository, authTokenRepository)
    val userService = DefaultUserService(userRepository)
    val productCatalogDataSource = ProductCatalogDataSource()
    val shoppingCartService = DefaultShoppingCartService(shoppingCartRepository, productCatalogDataSource)
    val loginService = LoginService(authService, shoppingCartService)
    val checkoutService = DefaultCheckoutService(shoppingCartService, StripePaymentProvider())
    val httpModule = HttpModule(
        routes = listOf(
            Route("POST", "/register", RegisterHandler(userService, authService, shoppingCartService)),
            Route("POST", "/login", LoginHandler(loginService)),
            Route("GET", "/auth/status", AuthStatusHandler(authService)),
            Route("GET", "/client/info", GetClientInfoHandler(authService)),
            Route("GET", "/products", GetProductsHandler(productCatalogDataSource)),
            Route("GET", "/products/:id", GetProductByIdHandler(productCatalogDataSource)),
            Route("GET", "/cart", GetCartHandler(authService, shoppingCartService)),
            Route("POST", "/cart", SaveCartHandler(shoppingCartService, authService)),
            Route("POST", "/cart/clear", ClearCartHandler(shoppingCartService)),
            Route("POST", "/checkout", CreateCheckoutHandler(checkoutService))
        )
    )
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    Server(httpModule, port).start()
    println("ShoppingCart server listening on http://localhost:$port")
}
