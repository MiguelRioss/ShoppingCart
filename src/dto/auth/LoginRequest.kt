package dto.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import stringValueIncludingBlank

/**
 * HTTP request body for `POST /login`.
 *
 * @property email user email address
 * @property password plain-text password from the request body
 * @property sessionId optional guest cart session id to attach after login
 */
data class LoginRequest(
    val email: String?,
    val password: String?,
    val sessionId: String? = null
)

/**
 * Parses the raw JSON body for `POST /login`.
 */
fun parseLoginRequest(requestBody: String, json: Json = Json): LoginRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject

    return LoginRequest(
        email = body.stringValueIncludingBlank("email"),
        password = body.stringValueIncludingBlank("password"),
        sessionId = body.stringValueIncludingBlank("sessionId")
    )
}
