package dto.auth

import booleanValue
import domain.user.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import objectValueIncludingNull
import stringValueIncludingBlank

/**
 * HTTP request body for `PUT /account`.
 *
 * Every field is optional. During conversion, missing fields keep the current
 * value from the existing [User].
 */
data class UpdateAccountRequest(
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
    val sameAsDeliveryAddress: Boolean?,
    val invoiceCompany: String?,
    val invoiceAddressLine1: String?,
    val invoiceAddressLine2: String?,
    val invoiceTownOrCity: String?,
    val invoicePostcode: String?,
    val invoiceCountry: String?,
    val vatNumber: String?,
    val projectNotes: String?
)

/**
 * Applies account updates to an existing user entity.
 *
 * @param user current persisted user
 * @param passwordHash optional already-hashed replacement password
 */
fun UpdateAccountRequest.toEntity(
    user: User,
    passwordHash: String? = null
) = user.copy(
    email = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: user.email,
    passwordHash = passwordHash ?: user.passwordHash,
    firstName = firstName?.trim() ?: user.firstName,
    lastName = lastName?.trim() ?: user.lastName,
    phone = phone?.trim() ?: user.phone,
    customerType = customerType?.name ?: user.customerType,
    deliveryCompany = deliveryCompany ?: user.deliveryCompany,
    deliveryAddressLine1 = deliveryAddressLine1 ?: user.deliveryAddressLine1,
    deliveryAddressLine2 = deliveryAddressLine2 ?: user.deliveryAddressLine2,
    deliveryTownOrCity = deliveryTownOrCity ?: user.deliveryTownOrCity,
    deliveryPostcode = deliveryPostcode ?: user.deliveryPostcode,
    deliveryCountry = deliveryCountry ?: user.deliveryCountry,
    sameAsDeliveryAddress = sameAsDeliveryAddress ?: user.sameAsDeliveryAddress,
    invoiceCompany = invoiceCompany ?: user.invoiceCompany,
    invoiceAddressLine1 = invoiceAddressLine1 ?: user.invoiceAddressLine1,
    invoiceAddressLine2 = invoiceAddressLine2 ?: user.invoiceAddressLine2,
    invoiceTownOrCity = invoiceTownOrCity ?: user.invoiceTownOrCity,
    invoicePostcode = invoicePostcode ?: user.invoicePostcode,
    invoiceCountry = invoiceCountry ?: user.invoiceCountry,
    vatNumber = vatNumber ?: user.vatNumber,
    projectNotes = projectNotes ?: user.projectNotes
)

/**
 * Parses the raw JSON body for `PUT /account`.
 */
fun parseUpdateAccountRequest(requestBody: String): UpdateAccountRequest {
    val body = Json.parseToJsonElement(requestBody).jsonObject
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
