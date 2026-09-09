package http

import dto.auth.AuthStatusResponse
import dto.auth.CustomerType
import dto.auth.LoginRequest
import dto.auth.LoginResponse
import dto.auth.RegisterUserRequest
import dto.auth.UpdateAccountRequest
import dto.cart.ClearShoppingCartRequest
import dto.cart.SaveShoppingCartProductRequest
import dto.cart.SaveShoppingCartRequest
import dto.cart.ShoppingCartProductResponse
import dto.cart.ShoppingCartResponse
import dto.checkout.CheckoutResponse
import dto.checkout.CheckoutShippingChargeRequest
import dto.checkout.CheckoutDestinationRequest
import dto.checkout.CheckoutRequest
import dto.user.UserInfoResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import support.decimalValue
import support.objectValueOrNull
import support.optionalDecimalValue
import support.stringListValue
import support.stringValue

fun parseLoginRequest(requestBody: String, json: Json = Json): LoginRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject

    return LoginRequest(
        email = body.stringValueIncludingBlank("email"),
        password = body.stringValueIncludingBlank("password"),
        sessionId = body.stringValueIncludingBlank("sessionId")
    )
}

fun parseRegisterUserRequest(requestBody: String, json: Json = Json): RegisterUserRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject
    val sameAsDeliveryAddress = body.booleanValue("sameAsDeliveryAddress") ?: true
    val deliveryAddress = body.objectValueIncludingNull("deliveryAddress")?.toAddressFields()
    val invoiceAddress = if (sameAsDeliveryAddress) {
        deliveryAddress
    } else {
        body.objectValueIncludingNull("invoiceAddress")?.toAddressFields()
    }

    return RegisterUserRequest(
        firstName = body.stringValueIncludingBlank("firstName"),
        lastName = body.stringValueIncludingBlank("lastName"),
        email = body.stringValueIncludingBlank("email"),
        password = body.stringValueIncludingBlank("password"),
        phone = body.stringValueIncludingBlank("phone"),
        customerType = CustomerType.from(body.stringValueIncludingBlank("customerType")),
        deliveryCompany = deliveryAddress?.company,
        deliveryAddressLine1 = deliveryAddress?.addressLine1,
        deliveryAddressLine2 = deliveryAddress?.addressLine2,
        deliveryTownOrCity = deliveryAddress?.townOrCity,
        deliveryPostcode = deliveryAddress?.postcode,
        deliveryCountry = deliveryAddress?.country,
        sameAsDeliveryAddress = sameAsDeliveryAddress,
        invoiceCompany = invoiceAddress?.company,
        invoiceAddressLine1 = invoiceAddress?.addressLine1,
        invoiceAddressLine2 = invoiceAddress?.addressLine2,
        invoiceTownOrCity = invoiceAddress?.townOrCity,
        invoicePostcode = invoiceAddress?.postcode,
        invoiceCountry = invoiceAddress?.country,
        vatNumber = body.stringValueIncludingBlank("vatNumber"),
        projectNotes = body.stringValueIncludingBlank("projectNotes"),
        sessionId = body.stringValueIncludingBlank("sessionId")
    )
}

fun parseUpdateAccountRequest(requestBody: String, json: Json = Json): UpdateAccountRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject
    val deliveryAddress = body.objectValueIncludingNull("deliveryAddress")?.toAddressFields()
    val invoiceAddress = body.objectValueIncludingNull("invoiceAddress")?.toAddressFields()

    return UpdateAccountRequest(
        firstName = body.stringValueIncludingBlank("firstName"),
        lastName = body.stringValueIncludingBlank("lastName"),
        email = body.stringValueIncludingBlank("email"),
        password = body.stringValueIncludingBlank("password"),
        phone = body.stringValueIncludingBlank("phone"),
        customerType = CustomerType.from(body.stringValueIncludingBlank("customerType")),
        deliveryCompany = deliveryAddress?.company,
        deliveryAddressLine1 = deliveryAddress?.addressLine1,
        deliveryAddressLine2 = deliveryAddress?.addressLine2,
        deliveryTownOrCity = deliveryAddress?.townOrCity,
        deliveryPostcode = deliveryAddress?.postcode,
        deliveryCountry = deliveryAddress?.country,
        sameAsDeliveryAddress = body.booleanValue("sameAsDeliveryAddress"),
        invoiceCompany = invoiceAddress?.company,
        invoiceAddressLine1 = invoiceAddress?.addressLine1,
        invoiceAddressLine2 = invoiceAddress?.addressLine2,
        invoiceTownOrCity = invoiceAddress?.townOrCity,
        invoicePostcode = invoiceAddress?.postcode,
        invoiceCountry = invoiceAddress?.country,
        vatNumber = body.stringValueIncludingBlank("vatNumber"),
        projectNotes = body.stringValueIncludingBlank("projectNotes")
    )
}

fun parseSaveShoppingCartRequest(requestBody: String, json: Json = Json): SaveShoppingCartRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject

    return SaveShoppingCartRequest(
        sessionId = body.stringValueIncludingBlank("sessionId"),
        products = body["products"]?.let { products ->
            runCatching {
                products.jsonArray.map { it.toSaveShoppingCartProductRequest() }
            }.getOrDefault(emptyList())
        } ?: emptyList()
    )
}

fun parseClearShoppingCartRequest(requestBody: String, json: Json = Json): ClearShoppingCartRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject

    return ClearShoppingCartRequest(
        sessionId = body.stringValueIncludingBlank("sessionId")
    )
}

fun parseCreateCheckoutRequest(requestBody: String, json: Json = Json): CheckoutRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject

    return CheckoutRequest(
        sessionId = body.stringValueIncludingBlank("sessionId"),
        successUrl = body.stringValueIncludingBlank("successUrl"),
        cancelUrl = body.stringValueIncludingBlank("cancelUrl"),
        customerEmail = body.stringValueIncludingBlank("customerEmail"),
        destination = body.objectValueOrNull("destination")
            ?.let { destination ->
                CheckoutDestinationRequest(
                    streetLines = destination.stringListValue("streetLines"),
                    countryCode = destination.stringValue("countryCode"),
                    postalCode = destination.stringValue("postalCode"),
                    city = destination.stringValue("city"),
                    currency = destination.stringValue("currency")
                )
            },
        shippingCharge = body.objectValueOrNull("shippingCharge")
            ?.let { shipping ->
                CheckoutShippingChargeRequest(
                    serviceType = shipping.stringValue("serviceType"),
                    serviceName = shipping.stringValue("serviceName"),
                    amountTotal = shipping.decimalValue("amountTotal"),
                    currency = shipping.stringValue("currency"),
                    estimatedDeliveryDate = shipping.stringValue("estimatedDeliveryDate"),
                    originalAmount = shipping.optionalDecimalValue("originalAmount"),
                    originalCurrency = shipping.stringValue("originalCurrency")
                )
            }
    )
}

fun LoginResponse.toJson(): String =
    buildJsonObject {
        put("token", JsonPrimitive(token))
        put("userId", JsonPrimitive(userId))
        put("expiresAt", JsonPrimitive(expiresAt))
    }.toString()

fun AuthStatusResponse.toJson(): String =
    buildJsonObject {
        put("authenticated", JsonPrimitive(authenticated))
        put("userId", JsonPrimitive(userId))
        put("email", JsonPrimitive(email))
    }.toString()

fun UserInfoResponse.toJson(): String =
    buildJsonObject {
        put("id", JsonPrimitive(id.toString()))
        put("email", JsonPrimitive(email))
        put("createdAt", JsonPrimitive(createdAt.toString()))
        putNullable("firstName", firstName)
        putNullable("lastName", lastName)
        putNullable("phone", phone)
        putNullable("customerType", customerType)
        putNullable("deliveryCompany", deliveryCompany)
        putNullable("deliveryAddressLine1", deliveryAddressLine1)
        putNullable("deliveryAddressLine2", deliveryAddressLine2)
        putNullable("deliveryTownOrCity", deliveryTownOrCity)
        putNullable("deliveryPostcode", deliveryPostcode)
        putNullable("deliveryCountry", deliveryCountry)
        put("sameAsDeliveryAddress", JsonPrimitive(sameAsDeliveryAddress))
        putNullable("invoiceCompany", invoiceCompany)
        putNullable("invoiceAddressLine1", invoiceAddressLine1)
        putNullable("invoiceAddressLine2", invoiceAddressLine2)
        putNullable("invoiceTownOrCity", invoiceTownOrCity)
        putNullable("invoicePostcode", invoicePostcode)
        putNullable("invoiceCountry", invoiceCountry)
        putNullable("vatNumber", vatNumber)
        putNullable("projectNotes", projectNotes)
    }.toString()

fun ShoppingCartResponse.toJson(): String =
    buildJsonObject {
        put("id", JsonPrimitive(id.toString()))
        userId?.let { put("userId", JsonPrimitive(it.toString())) }
        sessionId?.let { put("sessionId", JsonPrimitive(it)) }
        put("dateTime", JsonPrimitive(dateTime.toString()))
        put(
            "products",
            buildJsonArray {
                products.forEach { product ->
                    add(product.toJsonObject())
                }
            }
        )
    }.toString()

fun CheckoutResponse.toJson(): String =
    buildJsonObject {
        put("id", JsonPrimitive(id))
        put("provider", JsonPrimitive(provider))
        put("status", JsonPrimitive(status))
        url?.let { put("url", JsonPrimitive(it)) }
    }.toString()

private data class AddressFields(
    val company: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val townOrCity: String?,
    val postcode: String?,
    val country: String?
)

private fun JsonObject.toAddressFields(): AddressFields =
    AddressFields(
        company = stringValueIncludingBlank("company"),
        addressLine1 = stringValueIncludingBlank("addressLine1"),
        addressLine2 = stringValueIncludingBlank("addressLine2"),
        townOrCity = stringValueIncludingBlank("townOrCity"),
        postcode = stringValueIncludingBlank("postcode"),
        country = stringValueIncludingBlank("country")
    )

private fun JsonElement.toSaveShoppingCartProductRequest(): SaveShoppingCartProductRequest {
    val body = jsonObject

    return SaveShoppingCartProductRequest(
        productId = body["productId"]?.jsonPrimitive?.longOrNull,
        quantityM2 = body["quantityM2"]?.jsonPrimitive?.doubleOrNull
    )
}

private fun ShoppingCartProductResponse.toJsonObject(): JsonObject =
    buildJsonObject {
        put("productId", JsonPrimitive(productId))
        put("squareMeters", JsonPrimitive(squareMeters))
        put("amountBoxes", JsonPrimitive(amountBoxes))
        put("totalPricePerProduct", JsonPrimitive(totalPricePerProduct.toPlainString()))
    }

private fun JsonObjectBuilder.putNullable(name: String, value: String?) {
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

private fun JsonObject.stringValueIncludingBlank(name: String): String? =
    this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.contentOrNull

private fun JsonObject.objectValueIncludingNull(name: String): JsonObject? =
    this[name]?.takeUnless { it is JsonNull }?.jsonObject

private fun JsonObject.booleanValue(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull
