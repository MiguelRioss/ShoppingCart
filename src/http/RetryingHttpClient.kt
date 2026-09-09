/**
 * Small retry wrapper around Java HttpClient for transient external API failures.
 */
package http

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class RetryingHttpClient(
    private val httpClient: HttpClient,
    private val maxAttempts: Int = 4,
    private val retryableStatusCodes: Set<Int> = setOf(
        429,
        500,
        502,
        503,
        504
    )
) {

    fun send(
        request: HttpRequest
    ): HttpResponse<ByteArray> {

        var lastResponse: HttpResponse<ByteArray>? = null
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {

            try {
                val response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
                )

                lastResponse = response

                if (response.statusCode() !in retryableStatusCodes) {
                    return response
                }

                if (attempt < maxAttempts) {

                    val delayMillis = retryDelayMillis(
                        response = response,
                        attempt = attempt
                    )

                    Thread.sleep(delayMillis)
                }

            } catch (e: Exception) {

                lastException = e

                if (attempt < maxAttempts) {

                    val delayMillis = backoffMillis(attempt)

                    Thread.sleep(delayMillis)
                }
            }
        }

        if (lastResponse != null) {
            return lastResponse
        }

        throw IllegalStateException(
            "HTTP request failed after $maxAttempts attempts",
            lastException
        )
    }

    private fun retryDelayMillis(
        response: HttpResponse<ByteArray>,
        attempt: Int
    ): Long {

        val retryAfterSeconds = response.headers()
            .firstValue("Retry-After")
            .orElse(null)
            ?.toLongOrNull()

        return retryAfterSeconds
            ?.times(1000L)
            ?: backoffMillis(attempt)
    }

    private fun backoffMillis(
        attempt: Int
    ): Long =
        when (attempt) {
            1 -> 1_000L
            2 -> 2_000L
            3 -> 4_000L
            else -> 8_000L
        }
}

