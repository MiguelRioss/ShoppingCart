package http.auth

import dto.RegisterAccountResponse
import dto.RegisterUserRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.UserService

class RegisterHandler(
    private val userService: UserService,
    private val json: Json = Json
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val registerRequest = runCatching {
            RegisterUserRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        val user = runCatching {
            userService.registerUser(registerRequest)
        }.getOrElse {
            if (it.message == "User already exists") {
                return HttpError.Conflict.toResponse(it.message ?: "User already exists")
            }

            return HttpError.InvalidJsonRequestBody.toResponse(it.message ?: "Invalid registration request")
        }

        return HttpResponse(
            statusCode = 201,
            body = RegisterAccountResponse(
                userId = user.id.toString(),
                email = user.email
            ).toJson()
        )
    }
}
