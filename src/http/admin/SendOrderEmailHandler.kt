package http.admin

import http.*
import kotlinx.serialization.json.*
import services.auth.AuthServiceInterface
import services.orderemail.*
import java.util.UUID

/** Explicit admin action; PDFs are generated in memory and forwarded only on this POST. */
class SendOrderEmailHandler(auth:AuthServiceInterface,private val service:SendOrderEmailService,private val adminUserIds:Set<UUID>):RequestWithAuthHandler(auth) {
    override fun handleAuthenticated(request:AuthenticatedRequest):HttpResponse {
        if(request.user.id !in adminUserIds)return HttpResponse(403,"""{"message":"Administrator access required"}""")
        val body=runCatching{Json.parseToJsonElement(request.request.body).jsonObject}.getOrNull()
            ?:return HttpResponse(400,"""{"message":"JSON object required"}""")
        if(body.keys.any{it !in setOf("templateId","context","documentData")})return HttpResponse(400,"""{"message":"Only templateId, context and documentData are accepted"}""")
        val template=(body["templateId"] as? JsonPrimitive)?.contentOrNull?.let(OrderEmailTemplate::fromId)
            ?:return HttpResponse(400,"""{"message":"Unknown or missing templateId"}""")
        if(body.containsKey("context")&&body["context"] !is JsonObject)return HttpResponse(400,"""{"message":"context must be an object"}""")
        return try {
            val data=body["documentData"]?.let{OrderDocumentDataParser.parse(it as? JsonObject?:throw EmailPayloadValidationException(listOf("documentData")))}
            HttpResponse(202,service.send(request.request.pathParameter("orderId").orEmpty(),template,body["context"] as? JsonObject?:JsonObject(emptyMap()),data).toString())
        }catch(error:EmailPayloadValidationException){HttpResponse(422,buildJsonObject{put("message","Invalid email/document data");put("fields",JsonArray(error.fields.map(::JsonPrimitive)))}.toString())
        }catch(error:EmailPayloadConflictException){HttpResponse(409,buildJsonObject{put("message",error.message)}.toString())
        }catch(_:OrderEmailNotFoundException){HttpResponse(404,"""{"message":"Order not found"}""")
        }catch(_:EmailSenderConfigurationException){HttpResponse(503,"""{"message":"EMAIL_SENDER_BASE_URL is not configured correctly"}""")
        }catch(error:EmailSenderUnavailableException){HttpResponse(502,buildJsonObject{put("message","EMAIL_SENDER did not accept the email");error.upstreamStatus?.let{put("upstreamStatus",it)}}.toString())
        }catch(_:Exception){HttpResponse(500,"""{"message":"PDF generation or email submission failed; no success was recorded"}""")}
    }
}
