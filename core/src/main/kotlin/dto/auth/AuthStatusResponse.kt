package dto.auth

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Public response for `GET /auth/status`.
 *
 * @property authenticated true when the bearer token resolved to a user
 * @property userId id of the authenticated user
 * @property email email address of the authenticated user
 */
data class AuthStatusResponse(
    val authenticated: Boolean,
    val userId: String,
    val email: String
)

/**
 * Serializes the auth status response as compact JSON.
 */
fun AuthStatusResponse.toJson(): String =
    buildJsonObject {
        put("authenticated", JsonPrimitive(authenticated))
        put("userId", JsonPrimitive(userId))
        put("email", JsonPrimitive(email))
    }.toString()
