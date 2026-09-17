package domain.user

import java.time.LocalDateTime
import java.util.UUID

/**
 * Registered application user.
 *
 * @property id unique internal user id
 * @property email normalized email address used for login
 * @property passwordHash BCrypt hash of the user's password
 * @property createdAt UTC date/time when the user was created
 * @property firstName optional given name
 * @property lastName optional family name
 * @property phone optional customer phone number
 * @property customerType customer category stored from registration
 * @property deliveryCompany optional delivery company name
 * @property deliveryAddressLine1 first delivery address line
 * @property deliveryAddressLine2 optional second delivery address line
 * @property deliveryTownOrCity delivery town or city
 * @property deliveryPostcode delivery postal code
 * @property deliveryCountry delivery country
 * @property sameAsDeliveryAddress true when invoice address mirrors delivery address
 * @property invoiceCompany optional invoice company name
 * @property invoiceAddressLine1 first invoice address line
 * @property invoiceAddressLine2 optional second invoice address line
 * @property invoiceTownOrCity invoice town or city
 * @property invoicePostcode invoice postal code
 * @property invoiceCountry invoice country
 * @property vatNumber optional VAT number
 * @property projectNotes optional customer project notes
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


