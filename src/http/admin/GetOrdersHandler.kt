package http.admin

import db.OrderRepository
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import kotlinx.serialization.json.*
import services.auth.AuthServiceInterface
import java.util.UUID

/** Lists orders across all customers, exclusively for configured administrators. */
class GetOrdersHandler(
    auth: AuthServiceInterface,
    private val orders: OrderRepository,
    private val adminUserIds: Set<UUID>
) : RequestWithAuthHandler(auth) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        if (request.user.id !in adminUserIds) return HttpResponse(403, "{\"message\":\"Administrator access required\"}")
        return try {
            HttpResponse(200, buildJsonObject { put("orders", JsonArray(orders.listAll())) }.toString())
        } catch (_: Exception) {
            HttpResponse(503, "{\"message\":\"Unable to load orders\"}")
        }
    }
}
