package services

import db.UserRepository
import domain.User
import dto.RegisterUserRequest
import dto.UpdateAccountRequest
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * Default user service backed by a [UserRepository].
 *
 * @param userRepository persistence boundary for users
 * @param passwordHasher password hashing dependency
 * @param clock clock used to make user creation time testable
 */
class DefaultUserService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC()
) : UserService {
    /**
     * Registers a user after normalizing and validating input.
     *
     * @param email email address; trimmed and lowercased before storage
     * @param password plain text password; stored only as a hash
     * @throws IllegalArgumentException when email/password are blank or email already exists
     */
    override fun registerUser(email: String, password: String): User {
        val normalizedEmail = email.trim().lowercase()

        require(normalizedEmail.isNotBlank()) { "Email is required" }
        require(password.isNotBlank()) { "Password is required" }
        require(userRepository.getUserByEmail(normalizedEmail) == null) { "User already exists" }

        val user = User(
            id = UUID.randomUUID(),
            email = normalizedEmail,
            passwordHash = passwordHasher.hash(password),
            createdAt = LocalDateTime.now(clock)
        )

        return userRepository.saveUser(user)
    }

    override fun registerUser(request: RegisterUserRequest): User {
        val normalizedEmail = requireNotNull(request.email).trim().lowercase()
        val password = requireNotNull(request.password)

        require(userRepository.getUserByEmail(normalizedEmail) == null) { "User already exists" }

        val user = User(
            id = UUID.randomUUID(),
            email = normalizedEmail,
            passwordHash = passwordHasher.hash(password),
            createdAt = LocalDateTime.now(clock),
            firstName = request.firstName?.trim(),
            lastName = request.lastName?.trim(),
            phone = request.phone?.trim(),
            customerType = request.customerType?.name,
            deliveryCompany = request.deliveryAddress?.company,
            deliveryAddressLine1 = request.deliveryAddress?.addressLine1,
            deliveryAddressLine2 = request.deliveryAddress?.addressLine2,
            deliveryTownOrCity = request.deliveryAddress?.townOrCity,
            deliveryPostcode = request.deliveryAddress?.postcode,
            deliveryCountry = request.deliveryAddress?.country,
            sameAsDeliveryAddress = request.sameAsDeliveryAddress,
            invoiceCompany = request.invoiceAddress?.company,
            invoiceAddressLine1 = request.invoiceAddress?.addressLine1,
            invoiceAddressLine2 = request.invoiceAddress?.addressLine2,
            invoiceTownOrCity = request.invoiceAddress?.townOrCity,
            invoicePostcode = request.invoiceAddress?.postcode,
            invoiceCountry = request.invoiceAddress?.country,
            vatNumber = request.vatNumber,
            projectNotes = request.projectNotes
        )

        return userRepository.saveUser(user)
    }

    /**
     * Delegates user lookup to the repository.
     */
    override fun getUser(userId: UUID): User? =
        userRepository.getUser(userId)

    override fun updateUser(userId: UUID, request: UpdateAccountRequest): User {
        val user = requireNotNull(userRepository.getUser(userId)) { "User not found" }
        val normalizedEmail = request.email?.trim()?.lowercase()
        if (!normalizedEmail.isNullOrBlank() && normalizedEmail != user.email) {
            require(userRepository.getUserByEmail(normalizedEmail) == null) { "User already exists" }
        }

        val updatedUser = user.copy(
            email = normalizedEmail?.takeIf { it.isNotBlank() } ?: user.email,
            passwordHash = request.password?.takeIf { it.isNotBlank() }?.let { passwordHasher.hash(it) } ?: user.passwordHash,
            firstName = request.firstName?.trim() ?: user.firstName,
            lastName = request.lastName?.trim() ?: user.lastName,
            phone = request.phone?.trim() ?: user.phone,
            customerType = request.customerType?.name ?: user.customerType,
            deliveryCompany = request.deliveryAddress?.company ?: user.deliveryCompany,
            deliveryAddressLine1 = request.deliveryAddress?.addressLine1 ?: user.deliveryAddressLine1,
            deliveryAddressLine2 = request.deliveryAddress?.addressLine2 ?: user.deliveryAddressLine2,
            deliveryTownOrCity = request.deliveryAddress?.townOrCity ?: user.deliveryTownOrCity,
            deliveryPostcode = request.deliveryAddress?.postcode ?: user.deliveryPostcode,
            deliveryCountry = request.deliveryAddress?.country ?: user.deliveryCountry,
            sameAsDeliveryAddress = request.sameAsDeliveryAddress ?: user.sameAsDeliveryAddress,
            invoiceCompany = request.invoiceAddress?.company ?: user.invoiceCompany,
            invoiceAddressLine1 = request.invoiceAddress?.addressLine1 ?: user.invoiceAddressLine1,
            invoiceAddressLine2 = request.invoiceAddress?.addressLine2 ?: user.invoiceAddressLine2,
            invoiceTownOrCity = request.invoiceAddress?.townOrCity ?: user.invoiceTownOrCity,
            invoicePostcode = request.invoiceAddress?.postcode ?: user.invoicePostcode,
            invoiceCountry = request.invoiceAddress?.country ?: user.invoiceCountry,
            vatNumber = request.vatNumber ?: user.vatNumber,
            projectNotes = request.projectNotes ?: user.projectNotes
        )

        return userRepository.saveUser(updatedUser)
    }

    override fun deleteUser(userId: UUID): Boolean =
        userRepository.deleteUser(userId)
}
