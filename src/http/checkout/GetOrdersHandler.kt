package http.checkout

import db.OrderRepository
import http.*
import kotlinx.serialization.json.*
import services.auth.AuthServiceInterface

class GetOrdersHandler(private val orders: OrderRepository, auth: AuthServiceInterface) : RequestWithAuthHandler(auth) {
    override fun handleAuthenticated(request: AuthenticatedRequest) =
        HttpResponse(200, buildJsonObject { put("orders", JsonArray(orders.list(request.user.id))) }.toString())
}
