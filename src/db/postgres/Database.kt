package db.postgres

import config.Environment
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager
import java.nio.charset.StandardCharsets

/**
 * Opens JDBC connections using database settings from environment variables.
 */
class Database(
    private val url: String,
    private val user: String?,
    private val password: String?
) {
    init {
        DriverManager.setLoginTimeout(20)
    }

    fun getConnection(): Connection =
        if (user.isNullOrBlank()) {
            DriverManager.getConnection(url)
        } else {
            DriverManager.getConnection(url, user, password)
        }

    companion object {
        fun fromEnvironment(): Database? {
            val url = Environment.get("DATABASE_URL") ?: return null
            if (url.startsWith("postgresql://")) {
                return fromPostgresUrl(url)
            }

            return Database(
                url = url,
                user = Environment.get("DATABASE_USER"),
                password = Environment.get("DATABASE_PASSWORD")
            )
        }

        private fun fromPostgresUrl(url: String): Database {
            val uri = URI(url)
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            val jdbcUrl = "jdbc:postgresql://${uri.host}${port(uri)}/${uri.path.trimStart('/')}$query"
            val userInfo = uri.rawUserInfo?.split(":", limit = 2).orEmpty()

            return Database(
                url = jdbcUrl,
                user = userInfo.getOrNull(0)?.decodeUrl(),
                password = userInfo.getOrNull(1)?.decodeUrl()
            )
        }

        private fun port(uri: URI): String =
            if (uri.port == -1) "" else ":${uri.port}"

        private fun String.decodeUrl(): String =
            URLDecoder.decode(this, StandardCharsets.UTF_8)
    }
}
