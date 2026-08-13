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
import http.cart.GetCartHandler
import http.cart.SaveCartHandler
import http.client.GetClientInfoHandler
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.DefaultAuthService
import services.DefaultShoppingCartService
import services.DefaultUserService
import services.LoginService

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
    val loginService = LoginService(authService)
    val shoppingCartService = DefaultShoppingCartService(shoppingCartRepository, ProductCatalogDataSource())
    val httpModule = HttpModule(
        routes = listOf(
            Route("POST", "/register", RegisterHandler(userService, authService)),
            Route("POST", "/login", LoginHandler(loginService)),
            Route("GET", "/auth/status", AuthStatusHandler(authService)),
            Route("GET", "/client/info", GetClientInfoHandler(authService)),
            Route("POST", "/cart", SaveCartHandler(shoppingCartService)),
            Route("GET", "/cart", GetCartHandler(authService, shoppingCartService))
        )
    )
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    Server(httpModule, port).start()
    println("ShoppingCart server listening on http://localhost:$port")
}
