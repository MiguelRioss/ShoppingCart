import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryUserRepository
import http.HttpModule
import http.Route
import http.Server
import http.auth.LoginHandler
import services.DefaultAuthService
import services.LoginService

fun main() {
    val userRepository = InMemoryUserRepository()
    val authTokenRepository = InMemoryAuthTokenRepository()
    val authService = DefaultAuthService(userRepository, authTokenRepository)
    val loginService = LoginService(authService)
    val httpModule = HttpModule(
        routes = listOf(
            Route("POST", "/login", LoginHandler(loginService))
        )
    )
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    Server(httpModule, port).start()
    println("ShoppingCart server listening on http://localhost:$port")
}
