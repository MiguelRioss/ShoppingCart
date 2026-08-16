package http

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteTest {
    private val handler = object : RequestHandler {
        override fun handle(request: HttpRequest): HttpResponse = HttpResponse(200, "{}")
    }

    @Test
    fun `matches exact route paths`() {
        val route = Route("GET", "/products", handler)

        assertTrue(route.matches(HttpRequest(method = "GET", path = "/products", body = "")))
        assertFalse(route.matches(HttpRequest(method = "GET", path = "/products/9278", body = "")))
    }

    @Test
    fun `matches route paths with parameters`() {
        val route = Route("GET", "/products/:id", handler)
        val request = HttpRequest(method = "GET", path = "/products/9278", body = "")

        assertTrue(route.matches(request))
        assertEquals("9278", route.requestWithPathParameters(request).pathParameter("id"))
    }

    @Test
    fun `does not match parameter route with missing segment`() {
        val route = Route("GET", "/products/:id", handler)

        assertFalse(route.matches(HttpRequest(method = "GET", path = "/products", body = "")))
    }
}
