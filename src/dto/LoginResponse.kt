package dto

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class LoginResponse(
    val token: String,
    val userId: String,
    val expiresAt: String
) {
    fun toJson(): String =
        buildJsonObject {
            put("token", JsonPrimitive(token))
            put("userId", JsonPrimitive(userId))
            put("expiresAt", JsonPrimitive(expiresAt))
        }.toString()
}
