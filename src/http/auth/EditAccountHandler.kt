package http.auth

import dto.user.UserInfoRequest
import dto.user.toResponse
import dto.auth.UpdateAccountRequest
import http.AuthenticatedRequest
import http.HttpError
import http.HttpResponse
import http.RequestWithAuthHandler
import http.parseUpdateAccountRequest
import http.toJson
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
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        val updateRequest = runCatching {
            parseUpdateAccountRequest(request.request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        val updatedUser = runCatching {
            userService.updateUser(request.user.id, updateRequest)
        }.getOrElse {
            if (it.message == "User already exists") {
                return HttpError.Conflict.toResponse(it.message ?: "User already exists")
            }

            return HttpError.InvalidJsonRequestBody.toResponse(it.message ?: "Invalid account update request")
        }

        return HttpResponse(
            statusCode = 200,
            body = UserInfoRequest(updatedUser).toResponse().toJson()
        )
    }
}

