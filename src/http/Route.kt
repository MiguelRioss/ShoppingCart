package http

data class Route(
    val method: String,
    val path: String,
    val handler: RequestHandler
) {
    fun matches(request: HttpRequest): Boolean =
        method == request.method && path == request.path
}
