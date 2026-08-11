package http.auth

import dto.LoginRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.LoginService

class LoginHandler(
    private val loginService: LoginService,
    private val json: Json = Json
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val loginRequest = runCatching {
            LoginRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        val response = runCatching {
            loginService.login(
                email = loginRequest.email,
                password = loginRequest.password
            )
        }.getOrElse {
            val error = if (it.message == HttpError.MissingCredentials.defaultMessage) {
                HttpError.MissingCredentials
            } else {
                HttpError.Unauthorized
            }

            return error.toResponse()
        }

        return HttpResponse(200, response.toJson())
    }
}
