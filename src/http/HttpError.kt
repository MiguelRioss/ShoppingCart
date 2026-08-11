package http

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class HttpError(
    val statusCode: Int,
    val code: String,
    val defaultMessage: String
) {
    InvalidJsonRequestBody(400, "invalid_json_request_body", "Invalid JSON request body"),
    MissingCredentials(400, "missing_credentials", "Email and password are required"),
    Unauthorized(401, "unauthorized", "Invalid credentials"),
    NotFound(404, "not_found", "Not found"),
    InternalServerError(500, "internal_server_error", "Internal server error");

    fun toResponse(message: String = defaultMessage): HttpResponse {
        val body = buildJsonObject {
            put("error", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
        }.toString()

        return HttpResponse(statusCode, body)
    }
}
