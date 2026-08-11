package http

/**
 * Internal representation of an HTTP response.
 *
 * @param statusCode numeric HTTP status code
 * @param body raw JSON body to write to the client
 */
data class HttpResponse(
    val statusCode: Int,
    val body: String
)
