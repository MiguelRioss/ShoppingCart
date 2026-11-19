package services.orderemail

import config.Environment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.serialization.json.*

class EmailSenderUnavailableException(
    val upstreamStatus: Int? = null,
    val upstreamMessage: String? = null
) : RuntimeException(
    buildString {
        append("EMAIL_SENDER did not accept the request")
        upstreamStatus?.let { append(" (HTTP ").append(it).append(')') }
        upstreamMessage?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
    }
)

class EmailSenderConfigurationException(
    message: String = "Email Sender configuration is invalid"
) : RuntimeException(message)

/** HTTP implementation owns the Email Sender routes and request authentication. */
class HttpEmailSenderApi(
    private val baseUrl: () -> String? = { Environment.get("EMAIL_SENDER_BASE_URL") },
    private val apiKey: () -> String? = { Environment.get("EMAIL_SENDER_API_KEY") },
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : EmailSenderApi {
    private val logger = Logger.getLogger(javaClass.name)

    override fun startPurchaseComplete(payload: JsonObject): JsonObject =
        post("/v2/start-client-journey", payload)

    override fun sendEmail(payload: JsonObject): JsonObject =
        post("/v2/send", payload)

    private fun post(path: String, payload: JsonObject): JsonObject {
        val base = baseUrl()?.trim()?.trimEnd('/')?.takeIf(String::isNotBlank)
            ?: throw EmailSenderConfigurationException()
        val uri = runCatching { URI("$base$path") }.getOrNull()
            ?: throw EmailSenderConfigurationException()
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.rawQuery != null ||
            uri.rawFragment != null || uri.userInfo != null
        ) throw EmailSenderConfigurationException()
        val key = apiKey()?.takeIf(String::isNotBlank) ?: throw EmailSenderConfigurationException()
        val body = payload.toString()
        require(body.toByteArray().size <= 12 * 1024 * 1024) { "Email Sender payload exceeds 12 MiB" }
        val idempotencyKey = payload["idempotencyKey"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Email Sender payload requires idempotencyKey")
        val orderId = payload["order"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: payload["variables"]?.jsonObject?.get("order_reference")?.jsonPrimitive?.contentOrNull
            ?: "unknown"
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $key")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        logger.info("Email Sender request started orderId=$orderId host=${uri.host} path=$path")
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.log(Level.SEVERE, "Email Sender request interrupted orderId=$orderId host=${uri.host} path=$path", error)
            throw EmailSenderUnavailableException(upstreamMessage = error.javaClass.simpleName)
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Email Sender request failed before receiving a response orderId=$orderId host=${uri.host} path=$path", error)
            throw EmailSenderUnavailableException(upstreamMessage = error.javaClass.simpleName)
        }
        logger.info("Email Sender response received orderId=$orderId host=${uri.host} path=$path status=${response.statusCode()}")
        if (response.statusCode() !in 200..299) {
            val message = runCatching {
                val responseBody = Json.parseToJsonElement(response.body()).jsonObject
                val fields = responseBody["fields"]?.jsonArray?.joinToString()
                listOfNotNull(responseBody["message"]?.jsonPrimitive?.contentOrNull, fields)
                    .joinToString(" ").ifBlank { null }
            }.getOrNull() ?: response.body().take(1000)
            throw EmailSenderUnavailableException(response.statusCode(), message)
        }
        return runCatching { Json.parseToJsonElement(response.body()).jsonObject }
            .getOrElse { throw EmailSenderUnavailableException(response.statusCode(), "EMAIL_SENDER returned invalid JSON") }
    }
}
