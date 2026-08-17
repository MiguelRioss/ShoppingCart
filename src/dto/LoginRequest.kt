package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Request body accepted by POST /login.
 *
 * @param email user's login email; nullable so validation can report missing credentials cleanly
 * @param password user's plain text password from the request; nullable for the same reason
 * @param sessionId optional browser/session cart id to attach to the logged-in user
 */
data class LoginRequest(
    val email: String?,
    val password: String?,
    val sessionId: String? = null
) {
    companion object {
        /**
         * Parses a raw JSON request body into a login request.
         *
         * @param requestBody raw HTTP body
         * @param json JSON parser, injectable for tests
         * @throws kotlinx.serialization.SerializationException when the body is not valid JSON
         */
        fun fromJson(requestBody: String, json: Json = Json): LoginRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject

            return LoginRequest(
                email = body.stringValue("email"),
                password = body.stringValue("password"),
                sessionId = body.stringValue("sessionId")
            )
        }

        /**
         * Reads a nullable string field from a JSON object.
         */
        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.jsonPrimitive?.content
    }
}
