package services.checkout.payment.stripe

import domain.checkout.payment.CheckoutPaymentRequest
import domain.checkout.CheckoutSession
import domain.checkout.payment.PaymentEvent
import domain.checkout.payment.PaymentRefund
import domain.checkout.payment.PaymentWebhookRequest
import domain.checkout.payment.RefundPaymentRequest
import dto.checkout.payment.stripe.StripeCheckoutRequest
import dto.checkout.payment.stripe.StripeCheckoutResponse
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import services.checkout.payment.core.PaymentProvider
import domain.checkout.payment.PaymentProviderType

/** Stripe adapter responsible for creating and reading hosted Checkout Sessions. */
class StripePaymentProvider(
    private val config: StripeConfig =
        StripeConfig.fromEnvironment("PT")
) : PaymentProvider() {

    override val type: PaymentProviderType =
        PaymentProviderType.STRIPE

    override fun createCheckoutSession(
        request: CheckoutPaymentRequest
    ): CheckoutSession {

        val customerId = request.deliveryAddress?.let { createCustomer(request) }
        val stripeRequest = StripeCheckoutRequest.from(request, customerId)

        val response =
            sendRequest(
                HttpRequest.newBuilder(
                    URI.create(
                        config.checkoutApiUrl
                    )
                )
                    .timeout(
                        Duration.ofSeconds(20)
                    )
                    .header(
                        "Authorization",
                        "Bearer ${config.secretKey}"
                    )
                    .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                    )
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            stripeRequest.toFormBody()
                        )
                    )
                    .build()
            )

        return StripeCheckoutResponse
            .fromJson(response.body())
            .toCheckoutSession()
    }

    private fun createCustomer(request: CheckoutPaymentRequest): String {
        val address = requireNotNull(request.deliveryAddress)
        val name = requireNotNull(request.customerName?.takeIf(String::isNotBlank))
        val country = requireNotNull(request.destinationCountry)
        val fields = mutableListOf(
            "email" to requireNotNull(request.customerEmail),
            "name" to name,
            "address[line1]" to requireNotNull(address.addressLine1),
            "address[city]" to requireNotNull(address.townOrCity),
            "address[postal_code]" to requireNotNull(address.postcode),
            "address[country]" to country,
            "shipping[name]" to name,
            "shipping[address][line1]" to address.addressLine1,
            "shipping[address][city]" to address.townOrCity,
            "shipping[address][postal_code]" to address.postcode,
            "shipping[address][country]" to country
        )
        address.addressLine2?.takeIf(String::isNotBlank)?.let {
            fields += "address[line2]" to it
            fields += "shipping[address][line2]" to it
        }
        val form = fields.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        val url = config.checkoutApiUrl.removeSuffix("/checkout/sessions") + "/customers"
        val response = sendRequest(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer ${config.secretKey}")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", "shopping-cart-customer-${request.cartId}")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
        )
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("id").jsonPrimitive.content
    }

    override fun getCheckoutSession(
        checkoutSessionId: String
    ): CheckoutSession {

        val response =
            sendRequest(
                HttpRequest.newBuilder(
                    URI.create(
                        "${config.checkoutApiUrl}/$checkoutSessionId"
                    )
                )
                    .timeout(
                        Duration.ofSeconds(20)
                    )
                    .header(
                        "Authorization",
                        "Bearer ${config.secretKey}"
                    )
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .GET()
                    .build()
            )

        return StripeCheckoutResponse
            .fromJson(response.body())
            .toCheckoutSession()
    }

    override fun capturePayment(
        paymentId: String
    ): CheckoutSession {
        throw UnsupportedOperationException(
            "Stripe capture payment is not implemented yet"
        )
    }

    override fun refundPayment(
        request: RefundPaymentRequest
    ): PaymentRefund {
        throw UnsupportedOperationException(
            "Stripe refund is not implemented yet"
        )
    }

    override fun parseWebhook(
        request: PaymentWebhookRequest
    ): PaymentEvent {
        throw UnsupportedOperationException(
            "Stripe webhook is not implemented yet"
        )
    }
}
