package services.orderemail

import config.Environment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.*

fun interface ClientJourneyClient { fun start(payload: JsonObject): JsonObject }

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
class EmailSenderConfigurationException : RuntimeException("EMAIL_SENDER_BASE_URL is not configured")

/** No email or PDF rendering lives in ShoppingCart's paid-order callback. */
class StartClientJourneyService(
    private val mapper: StartClientJourneyMapper,
    private val client: ClientJourneyClient
) {
    fun onPaid(checkoutId: String) { client.start(mapper.fromPaidCheckout(checkoutId)) }
}

class HttpClientJourneyClient(
    private val baseUrl: () -> String? = { Environment.get("EMAIL_SENDER_BASE_URL") },
    private val apiKey: () -> String? = { Environment.get("EMAIL_SENDER_API_KEY") },
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : ClientJourneyClient {
    override fun start(payload: JsonObject): JsonObject {
        val base = baseUrl()?.trim()?.trimEnd('/')?.takeIf(String::isNotBlank)
            ?: throw EmailSenderConfigurationException()
        val uri = runCatching { URI("$base/v2/start-client-journey") }.getOrNull()
            ?: throw EmailSenderConfigurationException()
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null)
            throw EmailSenderConfigurationException()
        val key = apiKey()?.takeIf(String::isNotBlank) ?: throw EmailSenderConfigurationException()
        val body = payload.toString()
        require(body.toByteArray().size <= 12 * 1024 * 1024) { "Client journey payload exceeds 12 MiB" }
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $key")
            .header("Idempotency-Key", payload.getValue("idempotencyKey").jsonPrimitive.content)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        catch (error: InterruptedException) { Thread.currentThread().interrupt(); throw EmailSenderUnavailableException(upstreamMessage = error.javaClass.simpleName) }
        catch (error: Exception) { throw EmailSenderUnavailableException(upstreamMessage = error.javaClass.simpleName) }
        if (response.statusCode() !in 200..299) {
            val message = runCatching {
                val body = Json.parseToJsonElement(response.body()).jsonObject
                val fields = body["fields"]?.jsonArray?.joinToString()
                listOfNotNull(body["message"]?.jsonPrimitive?.contentOrNull, fields)
                    .joinToString(" ")
                    .ifBlank { null }
            }.getOrNull() ?: response.body().take(1000)
            throw EmailSenderUnavailableException(response.statusCode(), message)
        }
        return runCatching { Json.parseToJsonElement(response.body()).jsonObject }
            .getOrElse { throw EmailSenderUnavailableException(response.statusCode(), "EMAIL_SENDER returned invalid JSON") }
    }
}
