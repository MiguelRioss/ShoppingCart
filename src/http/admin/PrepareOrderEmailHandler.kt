package http.admin

import db.OrderRepository
import http.*
import kotlinx.serialization.json.*
import services.auth.AuthServiceInterface
import services.orderemail.*
import java.util.UUID

/** Read-only preparation endpoint. No EMAIL_SENDER calls or outgoing emails. */
class PrepareOrderEmailHandler(
    auth: AuthServiceInterface,
    private val orders: OrderRepository,
    private val builder: OrderEmailPayloadBuilder,
    private val adminUserIds: Set<UUID>
) : RequestWithAuthHandler(auth) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        if (request.user.id !in adminUserIds) return HttpResponse(403, """{"message":"Administrator access required"}""")
        val input = runCatching { Json.parseToJsonElement(request.request.body).jsonObject }.getOrNull()
            ?: return HttpResponse(400, """{"message":"JSON object required"}""")
        if (input.keys.any { it !in setOf("templateId", "context") }) return HttpResponse(400, """{"message":"Only templateId and context are accepted"}""")
        val id = (input["templateId"] as? JsonPrimitive)?.contentOrNull
        val template = id?.let(OrderEmailTemplate::fromId)
            ?: return HttpResponse(400, """{"message":"Unknown or missing templateId"}""")
        if (input.containsKey("context") && input["context"] !is JsonObject) return HttpResponse(400, """{"message":"context must be an object"}""")
        return try {
            val order = orders.findForEmail(request.request.pathParameter("orderId").orEmpty())
                ?: return HttpResponse(404, """{"message":"Order not found"}""")
            val payload = builder.prepare(template, order, input["context"] as? JsonObject ?: JsonObject(emptyMap()))
            HttpResponse(200, payload.toString())
        } catch (error: EmailPayloadValidationException) {
            HttpResponse(422, buildJsonObject {
                put("message", "Required rendering data is missing or invalid")
                put("fields", JsonArray(error.fields.map(::JsonPrimitive)))
            }.toString())
        } catch (error: EmailPayloadConflictException) {
            HttpResponse(409, buildJsonObject { put("message", error.message) }.toString())
        } catch (_: Exception) {
            HttpResponse(503, """{"message":"Unable to prepare order email"}""")
        }
    }
}
