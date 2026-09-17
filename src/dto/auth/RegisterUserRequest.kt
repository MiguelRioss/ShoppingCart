package dto.auth

import booleanValue
import domain.user.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import objectValueIncludingNull
import stringValueIncludingBlank
import java.time.LocalDateTime
import java.util.UUID

/**
 * HTTP request body for `POST /register`.
 *
 * The address fields are flattened after parsing so the service layer can build
 * a [User] without knowing the original JSON nesting.
 *
 * @property sessionId optional guest cart session id to attach to the new user
 */
data class RegisterUserRequest(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val password: String?,
    val phone: String?,
    val customerType: CustomerType?,
    val deliveryCompany: String?,
    val deliveryAddressLine1: String?,
    val deliveryAddressLine2: String?,
    val deliveryTownOrCity: String?,
    val deliveryPostcode: String?,
    val deliveryCountry: String?,
    val sameAsDeliveryAddress: Boolean,
    val invoiceCompany: String?,
    val invoiceAddressLine1: String?,
    val invoiceAddressLine2: String?,
    val invoiceTownOrCity: String?,
    val invoicePostcode: String?,
    val invoiceCountry: String?,
    val vatNumber: String?,
    val projectNotes: String?,
    val sessionId: String? = null
)

/**
 * Converts a validated registration request into a user entity.
 */
fun RegisterUserRequest.toEntity(
    id: UUID,
    passwordHash: String,
    createdAt: LocalDateTime
) = User(
    id = id,
    email = requireNotNull(email).trim().lowercase(),
    passwordHash = passwordHash,
    createdAt = createdAt,
    firstName = firstName?.trim(),
    lastName = lastName?.trim(),
    phone = phone?.trim(),
    customerType = customerType?.name,
    deliveryCompany = deliveryCompany,
    deliveryAddressLine1 = deliveryAddressLine1,
    deliveryAddressLine2 = deliveryAddressLine2,
    deliveryTownOrCity = deliveryTownOrCity,
    deliveryPostcode = deliveryPostcode,
    deliveryCountry = deliveryCountry,
    sameAsDeliveryAddress = sameAsDeliveryAddress,
    invoiceCompany = invoiceCompany,
    invoiceAddressLine1 = invoiceAddressLine1,
    invoiceAddressLine2 = invoiceAddressLine2,
    invoiceTownOrCity = invoiceTownOrCity,
    invoicePostcode = invoicePostcode,
    invoiceCountry = invoiceCountry,
    vatNumber = vatNumber,
    projectNotes = projectNotes
)

/**
 * Parses the raw JSON body for `POST /register`.
 */
fun parseRegisterUserRequest(requestBody: String): RegisterUserRequest {
    val body = Json.parseToJsonElement(requestBody).jsonObject
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
