package http

data class HttpRequest(
    val method: String,
    val path: String,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap()
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
}
