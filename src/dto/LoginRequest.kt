package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LoginRequest(
    val email: String?,
    val password: String?
) {
    companion object {
        fun fromJson(requestBody: String, json: Json = Json): LoginRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject

            return LoginRequest(
                email = body.stringValue("email"),
                password = body.stringValue("password")
            )
        }

        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.jsonPrimitive?.content
    }
}
