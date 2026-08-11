package http

/**
 * Contract implemented by all HTTP route handlers.
 */
interface RequestHandler {
    /**
     * Handles one request and returns the response to write.
     *
     * @param request normalized HTTP request
     * @return response status and JSON body
     */
    fun handle(request: HttpRequest): HttpResponse
}
