package http.auth

import dto.auth.parseUpdateAccountRequest
import dto.user.toJson
import dto.user.toResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpResponse
import http.RequestWithAuthHandler
import kotlinx.serialization.json.Json
import services.auth.AuthServiceInterface
import services.user.UserService

/**
 * Handles authenticated PUT /account requests.
 */
class EditAccountHandler(
    authService: AuthServiceInterface,
    private val userService: UserService,
    private val json: Json = Json
) : RequestWithAuthHandler(authService) {

    override fun handleAuthenticated(
        request: AuthenticatedRequest
    ): HttpResponse {

        val updateRequest =
            runCatching {
                parseUpdateAccountRequest(
                    request.request.body
                )
            }.getOrElse {
                return HttpError.InvalidJsonRequestBody
                    .toResponse()
            }

        val updatedUser =
            runCatching {
                userService.updateUser(
                    request.user.id,
                    updateRequest
                )
            }.getOrElse { error ->

                if (error.message == "User already exists") {
                    return HttpError.Conflict.toResponse(
                        error.message
                            ?: "User already exists"
                    )
                }

                return HttpError.InvalidJsonRequestBody
                    .toResponse(
                        error.message
                            ?: "Invalid account update request"
                    )
            }

        return HttpResponse(
            statusCode = 200,
            body =
                updatedUser
                    .toResponse()
                    .toJson()
        )
    }
}