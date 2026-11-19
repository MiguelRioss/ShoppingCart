package http.shipping

import config.Environment
import http.HttpResponse
import http.HttpRequest
import http.RequestHandler
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.serialization.json.*
import services.shipping.fulfillment.ShippingGoodsPreparationService

/** Authenticated system trigger for dated FedEx booking and the two fulfilment emails. */
class PrepareShippingGoodsHandler(
    private val service: ShippingGoodsPreparationService,
    private val apiKey: () -> String? = { Environment.get("SHIPPING_GOODS_API_KEY") },
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("Europe/Lisbon")) }
) : RequestHandler {
    private val logger = Logger.getLogger(javaClass.name)

    override fun handle(request: HttpRequest): HttpResponse {
        val expected = apiKey()?.takeIf(String::isNotBlank)
            ?: return response(503, "Shipping-goods API key is not configured")
        val supplied = request.header("Authorization")?.removePrefix("Bearer ")
            ?: return response(401, "Invalid authorization")
        if (!MessageDigest.isEqual(expected.toByteArray(), supplied.toByteArray()))
            return response(401, "Invalid authorization")

        val body = runCatching { Json.parseToJsonElement(request.body).jsonObject }.getOrNull()
            ?: return response(400, "Request body must be a JSON object")
        val orderId = body.text("orderId") ?: return response(400, "orderId is required")
        if (orderId.length > 128) return response(400, "orderId is too long")
        val dateText = body.text(
            "shippingDate") ?: return response(400, "shippingDate is required (YYYY-MM-DD)")
        val shippingDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
            ?: return response(400, "shippingDate must use YYYY-MM-DD")
        if (shippingDate.isBefore(today())) return response(400, "shippingDate cannot be in the past")

        return try {
            HttpResponse(200, service.prepare(orderId, shippingDate).toString())
        } catch (error: IllegalArgumentException) {
            val status = if (error.message?.startsWith("Order not found") == true) 404 else 422
            response(status, error.message ?: "Invalid shipping request")
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Shipping goods preparation failed orderId=$orderId", error)
            response(502, "Shipping preparation failed; verify carrier and email-sender logs")
        }
    }

    private fun response(status: Int, message: String): HttpResponse =
        HttpResponse(status, buildJsonObject { put("message", message) }.toString())

    private fun JsonObject.text(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
}
