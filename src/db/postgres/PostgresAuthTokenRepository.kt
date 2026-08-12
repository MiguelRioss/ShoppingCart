package db.postgres

import db.AuthTokenRepository
import domain.AuthToken
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class PostgresAuthTokenRepository(
    private val database: Database
) : AuthTokenRepository {
    override fun saveToken(authToken: AuthToken): AuthToken {
        database.getConnection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO auth_tokens (token, user_id, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (token) DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    expires_at = EXCLUDED.expires_at
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, authToken.token)
                statement.setObject(2, authToken.userId)
                statement.setTimestamp(3, Timestamp.valueOf(authToken.expiresAt))
                statement.executeUpdate()
            }
        }

        return authToken
    }

    override fun getToken(token: String): AuthToken? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT token, user_id, expires_at FROM auth_tokens WHERE token = ?").use { statement ->
                statement.setString(1, token)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.toAuthToken() else null
                }
            }
        }
    }

    private fun ResultSet.toAuthToken(): AuthToken =
        AuthToken(
            token = getString("token"),
            userId = getObject("user_id", UUID::class.java),
            expiresAt = getTimestamp("expires_at").toLocalDateTime()
        )
}
