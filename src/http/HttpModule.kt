package http

import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

class HttpModule(
    private val routes: List<Route>
) {
    fun handle(exchange: HttpExchange) {
        val request = exchange.toHttpRequest()
        val response = routes.firstOrNull { it.matches(request) }
            ?.handler
            ?.handle(request)
            ?: HttpError.NotFound.toResponse()

        exchange.writeJson(response)
    }

    private fun HttpExchange.toHttpRequest(): HttpRequest {
        val requestBody = requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        return HttpRequest(
            method = requestMethod,
            path = requestURI.path,
            body = requestBody,
            headers = requestHeaders.mapValues { it.value.toList() }
        )
    }

    private fun HttpExchange.writeJson(response: HttpResponse) {
        val responseBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(response.statusCode, responseBytes.size.toLong())
        responseBody.use { it.write(responseBytes) }
    }
}
