package db

import domain.AuthToken

interface AuthTokenRepository {
    fun saveToken(authToken: AuthToken): AuthToken
    fun getToken(token: String): AuthToken?
}
