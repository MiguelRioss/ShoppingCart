import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryShoppingCartRepository
import db.offline.InMemoryUserRepository
import http.HttpModule
import http.Route
import http.Server
import http.auth.LoginHandler
import http.cart.GetCartHandler
import services.DefaultAuthService
import services.DefaultShoppingCartService
import services.LoginService

fun main() {
    val userRepository = InMemoryUserRepository()
    val authTokenRepository = InMemoryAuthTokenRepository()
    val shoppingCartRepository = InMemoryShoppingCartRepository()
    val authService = DefaultAuthService(userRepository, authTokenRepository)
    val loginService = LoginService(authService)
    val shoppingCartService = DefaultShoppingCartService(shoppingCartRepository)
    val httpModule = HttpModule(
        routes = listOf(
            Route("POST", "/login", LoginHandler(loginService)),
            Route("GET", "/cart", GetCartHandler(authService, shoppingCartService))
        )
    )
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    Server(httpModule, port).start()
    println("ShoppingCart server listening on http://localhost:$port")
}
