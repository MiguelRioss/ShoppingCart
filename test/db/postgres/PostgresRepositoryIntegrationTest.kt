package db.postgres

import config.AppMode
import domain.AuthToken
import domain.ShoppingCart
import domain.ShoppingCartProduct
import domain.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostgresRepositoryIntegrationTest {
    private val database = if (AppMode.fromEnvironment() == AppMode.Online) {
        Database.fromEnvironment()
    } else {
        null
    }
    private val userRepository = database?.let { PostgresUserRepository(it) }
    private val authTokenRepository = database?.let { PostgresAuthTokenRepository(it) }
    private val shoppingCartRepository = database?.let { PostgresShoppingCartRepository(it) }
    private val user = User(
        id = UUID.fromString("10000000-0000-0000-0000-000000000010"),
        email = "postgres-test-user@example.com",
        passwordHash = "password-hash",
        createdAt = LocalDateTime.parse("2026-08-07T13:30:00"),
        firstName = "Jane",
        lastName = "Smith",
        phone = "+351 912 345 678",
        customerType = "PrivateCustomer",
        deliveryAddressLine1 = "Street and house number",
        deliveryTownOrCity = "Lisbon",
        deliveryPostcode = "1000-001",
        deliveryCountry = "Portugal"
    )
    private val authToken = AuthToken(
        token = "postgres-test-token",
        userId = user.id,
        expiresAt = LocalDateTime.parse("2026-08-07T14:30:00")
    )
    private val cart = ShoppingCart(
        id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        userId = user.id,
        dateTime = LocalDateTime.parse("2026-08-07T13:45:00"),
        products = listOf(
            ShoppingCartProduct(
                productId = 1864L,
                squareMeters = 12.5,
                amountBoxes = 3,
                totalPricePerProduct = BigDecimal("249.99")
            )
        )
    )

    @BeforeEach
    fun setUp() {
        assumeTrue(database != null, "APP_MODE is not online; skipping Postgres integration tests")
        PostgresSchema(requireNotNull(database)).migrate()
        cleanTestData()
    }

    @AfterEach
    fun tearDown() {
        if (database != null) cleanTestData()
    }

    @Test
    fun `saves and gets a user`() {
        userRepository!!.saveUser(user)

        assertEquals(user, userRepository.getUser(user.id))
        assertEquals(user, userRepository.getUserByEmail(user.email))
        assertNull(userRepository.getUser(UUID.fromString("10000000-0000-0000-0000-000000000099")))
    }

    @Test
    fun `saves and gets an auth token`() {
        userRepository!!.saveUser(user)

        authTokenRepository!!.saveToken(authToken)

        assertEquals(authToken, authTokenRepository.getToken(authToken.token))
        assertNull(authTokenRepository.getToken("missing-token"))
    }

    @Test
    fun `saves and gets a shopping cart`() {
        userRepository!!.saveUser(user)

        shoppingCartRepository!!.saveCart(cart)

        assertEquals(cart, shoppingCartRepository.getCartByUserId(user.id))
        assertNull(shoppingCartRepository.getCartByUserId(UUID.fromString("10000000-0000-0000-0000-000000000099")))
    }

    private fun cleanTestData() {
        database!!.getConnection().use { connection ->
            connection.prepareStatement("DELETE FROM shopping_carts WHERE id = ? OR user_id = ?").use { statement ->
                statement.setObject(1, cart.id)
                statement.setObject(2, user.id)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM auth_tokens WHERE token = ? OR user_id = ?").use { statement ->
                statement.setString(1, authToken.token)
                statement.setObject(2, user.id)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM users WHERE id = ? OR email = ?").use { statement ->
                statement.setObject(1, user.id)
                statement.setString(2, user.email)
                statement.executeUpdate()
            }
        }
    }
}
