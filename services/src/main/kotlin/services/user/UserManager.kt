package services.user

import db.UserRepository
import domain.user.User
import dto.auth.RegisterUserRequest
import dto.auth.UpdateAccountRequest
import dto.auth.toEntity
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import services.auth.PasswordHasher

/**
 * Default user service backed by a [UserRepository].
 *
 * @param userRepository persistence boundary for users
 * @param passwordHasher password hashing dependency
 * @param clock clock used to make user creation time testable
 */
class UserManager(
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
        request.requireRequiredFields()

        val normalizedEmail = requireNotNull(request.email).trim().lowercase()
        val password = requireNotNull(request.password)

        require(userRepository.getUserByEmail(normalizedEmail) == null) { "User already exists" }

        val user = request.toEntity(
            id = UUID.randomUUID(),
            passwordHash = passwordHasher.hash(password),
            createdAt = LocalDateTime.now(clock)
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

        val updatedUser = request.toEntity(
            user = user,
            passwordHash = request.password
                ?.takeIf { it.isNotBlank() }
                ?.let { passwordHasher.hash(it) }
        )

        return userRepository.saveUser(updatedUser)
    }

    override fun deleteUser(userId: UUID): Boolean =
        userRepository.deleteUser(userId)

    private fun RegisterUserRequest.requireRequiredFields() {
        require(!firstName.isNullOrBlank()) { "First name is required" }
        require(!lastName.isNullOrBlank()) { "Last name is required" }
        require(!email.isNullOrBlank()) { "Email is required" }
        require(!password.isNullOrBlank()) { "Password is required" }
        require(!phone.isNullOrBlank()) { "Phone is required" }
        require(customerType != null) { "Customer type is required" }
        requireRequiredAddress(
            line1 = deliveryAddressLine1,
            townOrCity = deliveryTownOrCity,
            postcode = deliveryPostcode,
            country = deliveryCountry,
            name = "Delivery address"
        )

        if (!sameAsDeliveryAddress) {
            requireRequiredAddress(
                line1 = invoiceAddressLine1,
                townOrCity = invoiceTownOrCity,
                postcode = invoicePostcode,
                country = invoiceCountry,
                name = "Invoice address"
            )
        }
    }

    private fun requireRequiredAddress(
        line1: String?,
        townOrCity: String?,
        postcode: String?,
        country: String?,
        name: String
    ) {
        require(!line1.isNullOrBlank()) { "$name line 1 is required" }
        require(!townOrCity.isNullOrBlank()) { "$name town or city is required" }
        require(!postcode.isNullOrBlank()) { "$name postcode is required" }
        require(!country.isNullOrBlank()) { "$name country is required" }
    }
}


