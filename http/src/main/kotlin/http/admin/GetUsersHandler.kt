package http.admin

import db.UserRepository
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import kotlinx.serialization.json.*
import services.auth.AuthServiceInterface
import java.util.UUID

/** Requires a valid login AND a server-configured administrator ID. Empty configuration denies all. */
class GetUsersHandler(
    authService: AuthServiceInterface,
    private val users: UserRepository,
    private val adminUserIds: Set<UUID>
) : RequestWithAuthHandler(authService) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        if (request.user.id !in adminUserIds) return HttpResponse(403, "{\"message\":\"Administrator access required\"}")
        return try {
            val body = buildJsonObject {
                put("users", buildJsonArray {
                    users.listUserSummaries().forEach { user ->
                        add(buildJsonObject {
                            put("id", user.id.toString())
                            put("email", user.email)
                            put("created_at", user.createdAt.toString())
                            put("first_name", user.firstName?.let(::JsonPrimitive) ?: JsonNull)
                            put("last_name", user.lastName?.let(::JsonPrimitive) ?: JsonNull)
                            put("phone", user.phone?.let(::JsonPrimitive) ?: JsonNull)
                        })
                    }
                })
            }
            HttpResponse(200, body.toString())
        } catch (_: Exception) {
            HttpResponse(503, "{\"message\":\"Unable to load users\"}")
        }
    }
}
