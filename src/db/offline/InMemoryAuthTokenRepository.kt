package db.offline

import db.AuthTokenRepository
import domain.AuthToken

/**
 * In-memory token repository used for local development and tests.
 *
 * Tokens are lost when the application restarts.
 */
class InMemoryAuthTokenRepository : AuthTokenRepository {
    private val tokens = mutableMapOf<String, AuthToken>()

    /**
     * Saves or replaces a token by its token string.
     */
    override fun saveToken(authToken: AuthToken): AuthToken {
        tokens[authToken.token] = authToken
        return authToken
    }

    /**
     * Returns a token by raw token value.
     */
    override fun getToken(token: String): AuthToken? = tokens[token]
}
