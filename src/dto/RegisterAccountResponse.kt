package dto

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class RegisterAccountResponse(
    val userId: String,
    val email: String
) {
    fun toJson(): String =
        buildJsonObject {
            put("userId", JsonPrimitive(userId))
            put("email", JsonPrimitive(email))
        }.toString()
}
