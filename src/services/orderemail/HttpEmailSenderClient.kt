package services.orderemail

import config.Environment
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface EmailSenderClient { fun send(payload: JsonObject): JsonObject }
class EmailSenderUnavailableException(val upstreamStatus: Int? = null) : RuntimeException("EMAIL_SENDER did not accept the email")
class EmailSenderConfigurationException : RuntimeException("EMAIL_SENDER_BASE_URL is not configured")

/** Sends exactly once per invocation. No hidden retries, redirects, email logs or disk writes. */
class HttpEmailSenderClient(
    private val baseUrl: () -> String? = { Environment.get("EMAIL_SENDER_BASE_URL") },
    private val apiKey: () -> String? = { Environment.get("EMAIL_SENDER_API_KEY") },
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : EmailSenderClient {
    override fun send(payload: JsonObject): JsonObject {
        val base = baseUrl()?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: throw EmailSenderConfigurationException()
        val uri = runCatching { URI("$base/v2/send") }.getOrNull() ?: throw EmailSenderConfigurationException()
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null) throw EmailSenderConfigurationException()
        val serialized = payload.toString()
        require(serialized.toByteArray().size <= 12 * 1024 * 1024) { "Email payload exceeds 12 MiB" }
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .header("Idempotency-Key", payload.getValue("idempotencyKey").jsonPrimitive.content)
        apiKey()?.takeIf { it.isNotBlank() }?.let { request.header("Authorization", "Bearer $it") }
        val response = try { client.send(request.POST(HttpRequest.BodyPublishers.ofString(serialized)).build(), HttpResponse.BodyHandlers.ofString()) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw EmailSenderUnavailableException() }
            catch (_: Exception) { throw EmailSenderUnavailableException() }
        if (response.statusCode() !in 200..299) throw EmailSenderUnavailableException(response.statusCode())
        return runCatching { Json.parseToJsonElement(response.body()).jsonObject }.getOrElse { throw EmailSenderUnavailableException(response.statusCode()) }
    }
}
