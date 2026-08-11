package dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginRequestTest {
    @Test
    fun `creates login request from json`() {
        val request = LoginRequest.fromJson("""{"email":"user@example.com","password":"password-123"}""")

        assertEquals("user@example.com", request.email)
        assertEquals("password-123", request.password)
    }

    @Test
    fun `fails for invalid json`() {
        assertFailsWith<IllegalArgumentException> {
            LoginRequest.fromJson("not-json")
        }
    }
}
