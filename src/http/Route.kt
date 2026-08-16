package http

/**
 * Route mapping used by [HttpModule].
 *
 * @param method HTTP method the route accepts
 * @param path URL path the route accepts; segments prefixed with : capture path parameters
 * @param handler request handler invoked when the route matches
 */
data class Route(
    val method: String,
    val path: String,
    val handler: RequestHandler
) {
    /**
     * Checks whether this route should handle a request.
     *
     * @param request normalized HTTP request
     * @return true when method and path match
     */
    fun matches(request: HttpRequest): Boolean =
        method == request.method && pathParameters(request.path) != null

    /**
     * Returns a copy of [request] with any path parameters captured from this route.
     */
    fun requestWithPathParameters(request: HttpRequest): HttpRequest =
        request.copy(pathParameters = pathParameters(request.path).orEmpty())

    private fun pathParameters(requestPath: String): Map<String, String>? {
        val routeSegments = path.trim('/').split('/').filter { it.isNotBlank() }
        val requestSegments = requestPath.trim('/').split('/').filter { it.isNotBlank() }

        if (routeSegments.size != requestSegments.size) {
            return null
        }

        return routeSegments.zip(requestSegments).fold(mutableMapOf<String, String>()) { parameters, (routeSegment, requestSegment) ->
            when {
                routeSegment.startsWith(":") -> parameters[routeSegment.removePrefix(":")] = requestSegment
                routeSegment != requestSegment -> return null
            }

            parameters
        }
    }
}
