package http.admin

import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.serialization.json.*
import services.auth.AuthServiceInterface
import services.shipping.fulfillment.ShippingGoodsPreparationService

/** Prepares a paid order's dated FedEx shipment for an authenticated administrator. */
class PrepareOrderShippingHandler(
    auth: AuthServiceInterface,
    private val service: ShippingGoodsPreparationService,
    private val adminUserIds: Set<UUID>,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("Europe/Lisbon")) }
) : RequestWithAuthHandler(auth) {
    private val logger = Logger.getLogger(javaClass.name)

    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        if (request.user.id !in adminUserIds) return response(403, "Administrator access required")

        val orderId = request.request.pathParameter("orderId")
            ?.takeIf(String::isNotBlank)
            ?: return response(400, "orderId is required")
        if (orderId.length > 128) return response(400, "orderId is too long")

        val body = runCatching { Json.parseToJsonElement(request.request.body).jsonObject }.getOrNull()
            ?: return response(400, "Request body must be a JSON object")
        val dateText = body.text("shippingDate")
            ?: return response(400, "shippingDate is required (YYYY-MM-DD)")
        val shippingDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
            ?: return response(400, "shippingDate must use YYYY-MM-DD")
        if (shippingDate.isBefore(today())) return response(400, "shippingDate cannot be in the past")

        return try {
            HttpResponse(200, service.prepare(orderId, shippingDate).toString())
        } catch (error: IllegalArgumentException) {
            val status = if (error.message?.startsWith("Order not found") == true) 404 else 422
            response(status, error.message ?: "Invalid shipping request")
        } catch (error: IllegalStateException) {
            val message = error.message.orEmpty()
            if (message.contains("already exists") || message.contains("manual review")) {
                response(409, "A shipment already exists for this order or needs manual review")
            } else {
                logger.log(Level.SEVERE, "Admin shipping preparation failed orderId=$orderId", error)
                response(502, "Shipping preparation failed; verify carrier and email-sender logs")
            }
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Admin shipping preparation failed orderId=$orderId", error)
            response(502, "Shipping preparation failed; verify carrier and email-sender logs")
        }
    }

    private fun response(status: Int, message: String): HttpResponse =
        HttpResponse(status, buildJsonObject { put("message", message) }.toString())

    private fun JsonObject.text(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
}
