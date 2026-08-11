package http

import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

/**
 * Converts low-level Java HTTP exchanges into project requests and dispatches them to routes.
 *
 * @param routes ordered route list; the first exact match handles the request
 */
class HttpModule(
    private val routes: List<Route>
) {
    /**
     * Handles one incoming HTTP exchange.
     *
     * @param exchange Java HTTP exchange received by the server
     */
    fun handle(exchange: HttpExchange) {
        exchange.addCorsHeaders()
        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        val request = exchange.toHttpRequest()
        val response = routes.firstOrNull { it.matches(request) }
            ?.handler
            ?.handle(request)
            ?: HttpError.NotFound.toResponse()

        exchange.writeJson(response)
    }

    /**
     * Allows the local HTML tester to call this API from a browser.
     */
    private fun HttpExchange.addCorsHeaders() {
        responseHeaders.add("Access-Control-Allow-Origin", "*")
        responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    /**
     * Converts an [HttpExchange] into the project's [HttpRequest] model.
     */
    private fun HttpExchange.toHttpRequest(): HttpRequest {
        val requestBody = requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        return HttpRequest(
            method = requestMethod,
            path = requestURI.path,
            body = requestBody,
            headers = requestHeaders.mapValues { it.value.toList() }
        )
    }

    /**
     * Writes a JSON response back to the client.
     */
    private fun HttpExchange.writeJson(response: HttpResponse) {
        val responseBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(response.statusCode, responseBytes.size.toLong())
        responseBody.use { it.write(responseBytes) }
    }
}
