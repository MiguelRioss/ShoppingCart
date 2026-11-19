package services.checkout.payment.core

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import services.common.ServiceErrorCode
import services.common.ServiceException

/**
 * Base class for HTTP-backed payment provider adapters.
 *
 * Subclasses build provider-specific requests and can use [sendRequest] to get
 * consistent timeout handling and service-level error conversion.
 */
abstract class PaymentProvider(
    protected val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
) : PaymentProviderInterface {

    /**
     * Sends an HTTP request and converts transport or non-2xx responses into [ServiceException].
     */
    protected fun sendRequest(
        request: HttpRequest
    ): HttpResponse<String> {

        val response =
            runCatching {
                httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                )
            }.getOrElse { error ->
                throw ServiceException(
                    errorCode =
                        ServiceErrorCode.CheckoutCreationFailed,
                    description =
                        "Payment provider request failed: " +
                                (error.message ?: "unknown error")
                )
            }

        if (response.statusCode() !in 200..299) {
            throw ServiceException(
                errorCode =
                    ServiceErrorCode.CheckoutCreationFailed,
                description =
                    "Payment provider request failed with status " +
                            "${response.statusCode()}: ${response.body()}"
            )
        }

        return response
    }
}
