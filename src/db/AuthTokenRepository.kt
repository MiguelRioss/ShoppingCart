package db

import domain.AuthToken

/**
 * Persistence boundary for authentication tokens.
 */
interface AuthTokenRepository {
    /**
     * Stores a token so future bearer-token requests can be resolved.
     *
     * @param authToken token domain object to persist
     * @return the saved token
     */
    fun saveToken(authToken: AuthToken): AuthToken

    /**
     * Looks up a token by its raw token value.
     *
     * @param token opaque token string, without the "Bearer " prefix
     * @return matching token, or null when it is unknown
     */
    fun getToken(token: String): AuthToken?
}
