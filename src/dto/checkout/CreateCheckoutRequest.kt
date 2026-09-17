package dto.checkout

import domain.checkout.CheckoutDeliveryAddress
import domain.checkout.CheckoutShippingCharge
import domain.checkout.CreateCheckoutSessionRequest
import dto.auth.toAddressFields
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import support.decimalValue
import support.objectValueOrNull
import support.optionalDecimalValue
import support.stringValue

/**
 * HTTP request body for `POST /checkout`.
 *
 * Nullable fields reflect raw JSON parsing. [toDomain] validates required
 * fields before the request reaches checkout business logic.
 *
 * @property sessionId session id for the cart being checked out
 * @property successUrl redirect URL used after successful payment
 * @property cancelUrl redirect URL used when the customer cancels checkout
 * @property customerEmail optional email to prefill or attach to checkout
 * @property shippingCharge optional caller-supplied shipping charge
 * @property deliveryAddress guest delivery address used when shipping is calculated
 */
data class CreateCheckoutRequest(
    val sessionId: String?,
    val successUrl: String?,
    val cancelUrl: String?,
    val customerEmail: String?,
    val shippingCharge: CheckoutShippingCharge? = null,
    val deliveryAddress: CheckoutDeliveryAddress? = null
)

/**
 * Parses the raw JSON body for `POST /checkout`.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid JSON
 * @throws IllegalArgumentException when required nested fields have the wrong shape
 */
fun parseCreateCheckoutRequest(
    requestBody: String,
    json: Json = Json
): CreateCheckoutRequest {

    val body =
        json.parseToJsonElement(
            requestBody
        ).jsonObject

    return CreateCheckoutRequest(
        sessionId =
            body.stringValueIncludingBlank("sessionId"),

        successUrl =
            body.stringValueIncludingBlank("successUrl"),

        cancelUrl =
            body.stringValueIncludingBlank("cancelUrl"),

        customerEmail =
            body.stringValueIncludingBlank("customerEmail"),

        shippingCharge =
            body.objectValueOrNull("shippingCharge")
                ?.let { shipping ->
                    CheckoutShippingCharge(
                        serviceType =
                            shipping.stringValue("serviceType"),

                        serviceName =
                            shipping.stringValue("serviceName"),

                        amountTotal =
                            shipping.decimalValue("amountTotal"),

                        currency =
                            shipping.stringValue("currency"),

                        estimatedDeliveryDate =
                            shipping.stringValue(
                                "estimatedDeliveryDate"
                            ),

                        originalAmount =
                            shipping.optionalDecimalValue(
                                "originalAmount"
                            ),

                        originalCurrency =
                            shipping.stringValue(
                                "originalCurrency"
                            )
                    )
                },

        deliveryAddress =
            body.objectValueOrNull("deliveryAddress")
                ?.toAddressFields()
                ?.let { address ->
                    CheckoutDeliveryAddress(
                        company = address.company,
                        addressLine1 = address.addressLine1,
                        addressLine2 = address.addressLine2,
                        townOrCity = address.townOrCity,
                        postcode = address.postcode,
                        country = address.country
                    )
                }
    )
}

/**
 * Converts a parsed HTTP checkout request into the validated domain command.
 *
 * @throws IllegalArgumentException when `sessionId`, `successUrl`, or `cancelUrl` is blank or missing
 */
fun CreateCheckoutRequest.toDomain(): CreateCheckoutSessionRequest =
    CreateCheckoutSessionRequest(
        sessionId =
            sessionId.required("sessionId"),

        successUrl =
            successUrl.required("successUrl"),

        cancelUrl =
            cancelUrl.required("cancelUrl"),

        customerEmail =
            customerEmail,

        shippingCharge =
            shippingCharge,

        deliveryAddress =
            deliveryAddress
    )

private fun String?.required(
    fieldName: String
): String {

    require(
        !isNullOrBlank()
    ) {
        "$fieldName is required"
    }

    return this
}

private fun JsonObject.stringValueIncludingBlank(
    name: String
): String? =
    this[name]
        ?.takeUnless {
            it is JsonNull
        }
        ?.jsonPrimitive
        ?.contentOrNull
