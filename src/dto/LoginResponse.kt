package dto

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Response body returned after a successful login.
 *
 * @param token bearer token clients use on authenticated requests
 * @param userId id of the authenticated user
 * @param expiresAt token expiry date/time as an ISO string
 */
data class LoginResponse(
    val token: String,
    val userId: String,
    val expiresAt: String
) {
    /**
     * Serializes the response to the JSON format returned by the HTTP API.
     */
    fun toJson(): String =
        buildJsonObject {
            put("token", JsonPrimitive(token))
            put("userId", JsonPrimitive(userId))
            put("expiresAt", JsonPrimitive(expiresAt))
        }.toString()
}
