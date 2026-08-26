package services

import config.Environment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class StripePaymentProvider(
    private val checkoutApiUrl: String? = Environment.get("STRIPE_CHECKOUT_API_URL")
        ?: Environment.get("STRIPE_SECRET_KEY")?.let { DEFAULT_CHECKOUT_API_URL },
    private val secretKey: String? = Environment.get("STRIPE_SECRET_KEY"),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val json: Json = Json
) : PaymentProvider {
    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        if (checkoutApiUrl.isNullOrBlank()) {
            return CheckoutSession(
                id = "stripe_pending_${request.cartId}",
                provider = "stripe",
                status = "api_link_missing",
                url = null
            )
        }

        if (secretKey.isNullOrBlank()) {
            throw ServiceException(
                errorCode = ServiceErrorCode.CheckoutCreationFailed,
                description = "STRIPE_SECRET_KEY is required to create a Stripe checkout session"
            )
        }

        val response = runCatching {
            httpClient.send(
                HttpRequest.newBuilder(URI.create(checkoutApiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer $secretKey")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toStripeFormBody()))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
        }.getOrElse {
            throw ServiceException(
                errorCode = ServiceErrorCode.CheckoutCreationFailed,
                description = "Stripe checkout request failed: ${it.message ?: "unknown error"}"
            )
        }

        if (response.statusCode() !in 200..299) {
            throw ServiceException(
                errorCode = ServiceErrorCode.CheckoutCreationFailed,
                description = "Stripe checkout request failed with status ${response.statusCode()}: ${response.body()}"
            )
        }

        val body = runCatching { json.parseToJsonElement(response.body()).jsonObject }
            .getOrElse {
                throw ServiceException(
                    errorCode = ServiceErrorCode.CheckoutCreationFailed,
                    description = "Stripe checkout response was not valid JSON"
                )
            }

        val id = body["id"]?.jsonPrimitive?.content
        val status = body["status"]?.jsonPrimitive?.content ?: "created"
        val url = body["url"]?.jsonPrimitive?.content

        if (id.isNullOrBlank()) {
            throw ServiceException(
                errorCode = ServiceErrorCode.CheckoutCreationFailed,
                description = "Stripe checkout response did not include a session id"
            )
        }

        return CheckoutSession(
            id = id,
            provider = "stripe",
            status = status,
            url = url
        )
    }

    private fun CheckoutRequest.toStripeFormBody(): String {
        val values = mutableListOf(
            "mode" to "payment",
            "locale" to "en",
            "payment_method_types[0]" to "card",
            "payment_method_types[1]" to "paypal",
            "payment_method_types[2]" to "link",
            "payment_method_types[3]" to "mb_way",
            "payment_method_types[4]" to "klarna",
            "success_url" to successUrl,
            "cancel_url" to cancelUrl,
            "client_reference_id" to cartId,
            "metadata[cart_id]" to cartId,
            "metadata[session_id]" to sessionId
        )

        customerEmail?.takeIf { it.isNotBlank() }?.let {
            values += "customer_email" to it
        }

        lineItems.forEachIndexed { index, item ->
            values += "line_items[$index][quantity]" to item.quantity.toString()
            values += "line_items[$index][price_data][currency]" to item.currency.lowercase()
            values += "line_items[$index][price_data][unit_amount]" to item.unitAmountInCents().toString()
            values += "line_items[$index][price_data][product_data][name]" to item.name
            values += "line_items[$index][price_data][product_data][metadata][product_id]" to item.productId.toString()
            item.imageUrl?.takeIf { it.isNotBlank() }?.let {
                values += "line_items[$index][price_data][product_data][images][0]" to it
            }
        }

        return values.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun CheckoutLineItem.unitAmountInCents(): Long =
        amountTotal.divide(BigDecimal(quantity), 2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .setScale(0)
            .longValueExact()

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private companion object {
        const val DEFAULT_CHECKOUT_API_URL = "https://api.stripe.com/v1/checkout/sessions"
    }
}
