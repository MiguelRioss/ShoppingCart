package http.admin

import db.offline.InMemoryUserRepository
import db.offline.InMemoryAuthTokenRepository
import http.HttpRequest
import services.auth.AuthManager
import services.user.UserManager
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GetUsersHandlerTest {
    private val users = InMemoryUserRepository()
    private val auth = AuthManager(users, InMemoryAuthTokenRepository())
    private val admin = UserManager(users).registerUser("admin@example.com", "password-123")
    private val customer = UserManager(users).registerUser("customer@example.com", "password-123")
    private fun request(token: String? = null) = HttpRequest("GET", "/admin/users", "",
        headers = token?.let { mapOf("Authorization" to listOf("Bearer $it")) }.orEmpty())

    @Test fun missingOrInvalidAuthenticationIsRejected() {
        val handler = GetUsersHandler(auth, users, setOf(admin.id))
        assertEquals(401, handler.handle(request()).statusCode)
        assertEquals(401, handler.handle(request("invalid")).statusCode)
    }
    @Test fun customerAndUnconfiguredAdministratorAreRejected() {
        val token = auth.login(customer.email, "password-123").token
        assertEquals(403, GetUsersHandler(auth, users, setOf(admin.id)).handle(request(token)).statusCode)
        val adminToken = auth.login(admin.email, "password-123").token
        assertEquals(403, GetUsersHandler(auth, users, emptySet()).handle(request(adminToken)).statusCode)
    }
    @Test fun administratorReceivesOnlyPermittedFields() {
        val token = auth.login(admin.email, "password-123").token
        val response = GetUsersHandler(auth, users, setOf(admin.id)).handle(request(token))
        assertEquals(200, response.statusCode)
        val rows = Json.parseToJsonElement(response.body).jsonObject.getValue("users").jsonArray
        assertEquals(2, rows.size)
        rows.forEach { assertEquals(setOf("id", "email", "created_at", "first_name", "last_name", "phone"), it.jsonObject.keys) }
        assertFalse(response.body.contains(admin.passwordHash))
        assertEquals(JsonNull, rows[0].jsonObject["phone"])
    }
    @Test fun repositoryErrorsDoNotExposeDetails() {
        val broken = object : db.UserRepository by users {
            override fun listUserSummaries(): List<domain.user.UserSummary> = error("secret connection details")
        }
        val token = auth.login(admin.email, "password-123").token
        val response = GetUsersHandler(auth, broken, setOf(admin.id)).handle(request(token))
        assertEquals(503, response.statusCode)
        assertFalse(response.body.contains("secret"))
    }
}
