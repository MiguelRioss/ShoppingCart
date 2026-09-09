package dto.auth

import domain.user.User
import java.time.LocalDateTime
import java.util.UUID

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
