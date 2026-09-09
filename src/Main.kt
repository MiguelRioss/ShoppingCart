import config.AppMode
import db.postgres.Database
import server.ShoppingCartServer

/**
 * Application entry point.
 */
fun main() {
    val database = if (AppMode.fromEnvironment() == AppMode.Online) {
        requireNotNull(Database.fromEnvironment()) { "DATABASE_URL is required when APP_MODE=online" }
    } else {
        null
    }

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    ShoppingCartServer(database, port).start()
}

