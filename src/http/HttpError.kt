package http

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import services.ServiceException

class HttpErrorResponse(
    val status: Int,
    val defaultMessage: String
) {
    fun toResponse(
        message: String = defaultMessage,
        description: String = message
    ): HttpResponse {
        val body = buildJsonObject {
            put("message", JsonPrimitive(message))
            put("description", JsonPrimitive(description))
        }.toString()

        return HttpResponse(status, body)
    }
}

object HttpStatusCodes {
    const val BadRequest = 400
    const val Unauthorized = 401
    const val NotFound = 404
    const val Conflict = 409
    const val InternalServerError = 500
}

object HttpError {
    val InvalidJsonRequestBody = HttpErrorResponse(
        status = HttpStatusCodes.BadRequest,
        defaultMessage = "Invalid JSON request body"
    )
    val MissingCredentials = HttpErrorResponse(
        status = HttpStatusCodes.BadRequest,
        defaultMessage = "Email and password are required"
    )
    val Unauthorized = HttpErrorResponse(
        status = HttpStatusCodes.Unauthorized,
        defaultMessage = "Invalid credentials"
    )
    val Conflict = HttpErrorResponse(
        status = HttpStatusCodes.Conflict,
        defaultMessage = "Conflict"
    )
    val NotFound = HttpErrorResponse(
        status = HttpStatusCodes.NotFound,
        defaultMessage = "Not found"
    )
    val InternalServerError = HttpErrorResponse(
        status = HttpStatusCodes.InternalServerError,
        defaultMessage = "Internal error. Contact your teacher!"
    )

    fun from(exception: ServiceException): HttpResponse =
        when (exception.errorCode.code) {
            1000 -> HttpErrorResponse(
                status = HttpStatusCodes.NotFound,
                defaultMessage = exception.message
            )
            1001,
            1002,
            1003,
            1004 -> HttpErrorResponse(
                status = HttpStatusCodes.BadRequest,
                defaultMessage = exception.message
            )
            else -> InternalServerError
        }.toResponse(description = exception.description)
}
