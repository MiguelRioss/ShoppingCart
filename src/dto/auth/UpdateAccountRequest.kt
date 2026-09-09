package dto.auth

import domain.user.User

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
