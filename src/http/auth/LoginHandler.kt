package http.auth

import dto.LoginRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.LoginService

/**
 * Handles POST /login requests.
 *
 * @param loginService service that validates credentials and builds the login response
 * @param json JSON parser used for request-body parsing
 */
class LoginHandler(
    private val loginService: LoginService,
    private val json: Json = Json
) : RequestHandler {
    /**
     * Parses login JSON, delegates login behavior, and maps failures to HTTP errors.
     *
     * @param request HTTP request containing email/password JSON
     * @return 200 with token data, 400 for invalid/missing input, or 401 for invalid credentials
     */
    override fun handle(request: HttpRequest): HttpResponse {
        val loginRequest = runCatching {
            LoginRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        val response = runCatching {
            loginService.login(
                email = loginRequest.email,
                password = loginRequest.password,
                sessionId = loginRequest.sessionId
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
