package dto.auth

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Public auth response returned after registration or login.
 *
 * @property token bearer token to send in authenticated requests
 * @property userId id of the authenticated user
 * @property expiresAt token expiration timestamp
 */
data class LoginResponse(
    val token: String,
    val userId: String,
    val expiresAt: String
)

/**
 * Serializes the auth response as compact JSON.
 */
fun LoginResponse.toJson(): String =
    buildJsonObject {
        put("token", JsonPrimitive(token))
        put("userId", JsonPrimitive(userId))
        put("expiresAt", JsonPrimitive(expiresAt))
    }.toString()
