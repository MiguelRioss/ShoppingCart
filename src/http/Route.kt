package http

/**
 * Static route mapping used by [HttpModule].
 *
 * @param method HTTP method the route accepts
 * @param path exact URL path the route accepts
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
     * @return true when method and path match exactly
     */
    fun matches(request: HttpRequest): Boolean =
        method == request.method && path == request.path
}
