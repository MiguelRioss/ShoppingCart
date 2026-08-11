package db.offline

import db.AuthTokenRepository
import domain.AuthToken

class InMemoryAuthTokenRepository : AuthTokenRepository {
    private val tokens = mutableMapOf<String, AuthToken>()

    override fun saveToken(authToken: AuthToken): AuthToken {
        tokens[authToken.token] = authToken
        return authToken
    }

    override fun getToken(token: String): AuthToken? = tokens[token]
}
