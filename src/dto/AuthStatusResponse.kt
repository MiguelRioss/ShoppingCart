package dto

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Response body returned when a bearer token resolves to an authenticated user.
 */
data class AuthStatusResponse(
    val authenticated: Boolean,
    val userId: String,
    val email: String
) {
    fun toJson(): String =
        buildJsonObject {
            put("authenticated", JsonPrimitive(authenticated))
            put("userId", JsonPrimitive(userId))
            put("email", JsonPrimitive(email))
        }.toString()
}
