package domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * Registered application user.
 *
 * @param id unique internal user id
 * @param email normalized email address used for login
 * @param passwordHash BCrypt hash of the user's password
 * @param createdAt UTC date/time when the user was created
 */
data class User(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val createdAt: LocalDateTime,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val customerType: String? = null,
    val deliveryCompany: String? = null,
    val deliveryAddressLine1: String? = null,
    val deliveryAddressLine2: String? = null,
    val deliveryTownOrCity: String? = null,
    val deliveryPostcode: String? = null,
    val deliveryCountry: String? = null,
    val sameAsDeliveryAddress: Boolean = true,
    val invoiceCompany: String? = null,
    val invoiceAddressLine1: String? = null,
    val invoiceAddressLine2: String? = null,
    val invoiceTownOrCity: String? = null,
    val invoicePostcode: String? = null,
    val invoiceCountry: String? = null,
    val vatNumber: String? = null,
    val projectNotes: String? = null
)
