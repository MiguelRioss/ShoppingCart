package http

data class HttpRequest(
    val method: String,
    val path: String,
    val body: String
)
