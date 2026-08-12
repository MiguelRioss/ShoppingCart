package http

data class HttpRequest(
    val method: String,
    val path: String,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap()
) {
    /**
     * Gets the first header value matching [name], ignoring case.
     */
    fun header(name: String): String? {
        val values = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        return values?.firstOrNull()
    }
}
