package dto.checkout.payment.stripe

import domain.checkout.CheckoutLineItem
import domain.checkout.payment.CheckoutPaymentRequest
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Stripe Checkout form request represented as ordered key/value pairs.
 * Nested Stripe parameters are encoded using bracket notation in [toFormBody].
 */
data class StripeCheckoutRequest(
    private val values: List<Pair<String, String>>
) {

    /** Encodes this request as `application/x-www-form-urlencoded`. */
    fun toFormBody(): String =
        values.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

    companion object {

        /** Maps provider-neutral checkout line items and metadata to Stripe fields. */
        fun from(
            request: CheckoutPaymentRequest
        ): StripeCheckoutRequest {

            val values =
                mutableListOf(
                    "mode" to "payment",
                    "locale" to "en",

                    "payment_method_types[0]" to "card",
                    "payment_method_types[1]" to "paypal",
                    "payment_method_types[2]" to "link",
                    "payment_method_types[3]" to "mb_way",
                    "payment_method_types[4]" to "klarna",

                    "success_url" to request.successUrl,
                    "cancel_url" to request.cancelUrl,

                    "client_reference_id" to request.cartId,

                    "metadata[cart_id]" to request.cartId,
                    "metadata[session_id]" to request.sessionId
                )

            request.customerEmail
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { email ->
                    values +=
                        "customer_email" to email
                }

            request.lineItems.forEachIndexed { index, item ->

                values +=
                    "line_items[$index][quantity]" to
                            item.quantity.toString()

                values +=
                    "line_items[$index][price_data][currency]" to
                            item.currency.lowercase()

                values +=
                    "line_items[$index][price_data][unit_amount]" to
                            item.unitAmountInCents().toString()

                values +=
                    "line_items[$index][price_data][product_data][name]" to
                            item.name

                values +=
                    "line_items[$index][price_data][product_data][metadata][product_id]" to
                            item.productId.toString()

                item.description
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let { description ->
                        values +=
                            "line_items[$index][price_data][product_data][description]" to
                                    description
                    }

                item.imageUrl
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let { imageUrl ->
                        values +=
                            "line_items[$index][price_data][product_data][images][0]" to
                                    imageUrl
                    }
            }

            return StripeCheckoutRequest(
                values = values
            )
        }
    }
}

private fun CheckoutLineItem.unitAmountInCents(): Long =
    amountTotal
        .divide(
            BigDecimal(quantity),
            2,
            RoundingMode.HALF_UP
        )
        .movePointRight(2)
        .setScale(0)
        .longValueExact()

private fun String.urlEncode(): String =
    URLEncoder.encode(
        this,
        StandardCharsets.UTF_8
    )
