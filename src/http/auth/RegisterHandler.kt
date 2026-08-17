package http.auth

import dto.LoginResponse
import dto.RegisterUserRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.AuthService
import services.ShoppingCartService
import services.UserService

class RegisterHandler(
    private val userService: UserService,
    private val authService: AuthService,
    private val shoppingCartService: ShoppingCartService? = null,
    private val json: Json = Json
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val registerRequest = runCatching {
            RegisterUserRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        runCatching {
            userService.registerUser(registerRequest)
        }.getOrElse {
            if (it.message == "User already exists") {
                return HttpError.Conflict.toResponse(it.message ?: "User already exists")
            }

            return HttpError.InvalidJsonRequestBody.toResponse(it.message ?: "Invalid registration request")
        }

        val authToken = authService.login(
            email = requireNotNull(registerRequest.email),
            password = requireNotNull(registerRequest.password)
        )
        if (!registerRequest.sessionId.isNullOrBlank()) {
            shoppingCartService?.associateCartWithUser(registerRequest.sessionId, authToken.userId)
        }

        return HttpResponse(
            statusCode = 201,
            body = LoginResponse(
                token = authToken.token,
                userId = authToken.userId.toString(),
                expiresAt = authToken.expiresAt.toString()
            ).toJson()
        )
    }
}
