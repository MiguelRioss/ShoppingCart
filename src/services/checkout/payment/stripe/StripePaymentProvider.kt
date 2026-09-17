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
import java.net.http.HttpRequest
import java.time.Duration
import services.checkout.payment.core.PaymentProvider
import services.checkout.payment.core.PaymentProviderType

class StripePaymentProvider(
    private val config: StripeConfig =
        StripeConfig.fromEnvironment()
) : PaymentProvider() {

    override val type: PaymentProviderType =
        PaymentProviderType.STRIPE

    override fun createCheckoutSession(
        request: CheckoutPaymentRequest
    ): CheckoutSession {

        val stripeRequest =
            StripeCheckoutRequest.from(request)

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