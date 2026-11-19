package db.postgres

import config.Environment
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.nio.charset.StandardCharsets
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Opens JDBC connections using database settings from environment variables.
 */
class Database(
    private val url: String,
    private val user: String?,
    private val password: String?
) : AutoCloseable {
    private val pool: HikariDataSource by lazy {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            if (!user.isNullOrBlank()) { username = user; this.password = this@Database.password }
            maximumPoolSize = 4
            minimumIdle = 1
            connectionTimeout = 60000
            initializationFailTimeout = 60000
            addDataSourceProperty("connectTimeout", "5")
            addDataSourceProperty("tcpKeepAlive", "true")
        })
    }

    fun getConnection(): Connection = pool.connection

    override fun close() { pool.close() }

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
