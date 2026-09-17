package dto.checkout.payment.stripe

import domain.checkout.CheckoutSession
import domain.checkout.CheckoutSessionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import services.checkout.payment.core.PaymentProviderType
import services.common.ServiceErrorCode
import services.common.ServiceException

data class StripeCheckoutResponse(
    val id: String,
    val status: String?,
    val url: String?
) {

    fun toCheckoutSession(): CheckoutSession =
        CheckoutSession(
            id = id,
            provider = PaymentProviderType.STRIPE,
            status =
                status
                    ?.let(
                        CheckoutSessionStatus::fromProviderStatus
                    )
                    ?: CheckoutSessionStatus.CREATED,
            url = url
        )

    companion object {

        fun fromJson(
            responseBody: String
        ): StripeCheckoutResponse {

            val body =
                runCatching {
                    Json.parseToJsonElement(
                        responseBody
                    ).jsonObject
                }.getOrElse {
                    throw ServiceException(
                        errorCode =
                            ServiceErrorCode.CheckoutCreationFailed,
                        description =
                            "Stripe response was not valid JSON"
                    )
                }

            val id =
                body["id"]
                    ?.jsonPrimitive
                    ?.contentOrNull

            if (id.isNullOrBlank()) {
                throw ServiceException(
                    errorCode =
                        ServiceErrorCode.CheckoutCreationFailed,
                    description =
                        "Stripe response did not include a session id"
                )
            }

            return StripeCheckoutResponse(
                id = id,
                status =
                    body["status"]
                        ?.jsonPrimitive
                        ?.contentOrNull,
                url =
                    body["url"]
                        ?.jsonPrimitive
                        ?.contentOrNull
            )
        }
    }
}