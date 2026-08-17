package http

data class HttpRequest(
    val method: String,
    val path: String,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val pathParameters: Map<String, String> = emptyMap()
) {
    /**
     * Gets the first header value matching [name], ignoring case.
     */
    fun header(name: String): String? {
        val values = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        return values?.firstOrNull()
    }

    /**
     * Gets a path parameter captured from a route such as /products/:id.
     */
    fun pathParameter(name: String): String? = pathParameters[name]

    /**
     * Gets the first query parameter value matching [name].
     */
    fun queryParameter(name: String): String? {
        val query = path.substringAfter("?", missingDelimiterValue = "")
        if (query.isBlank()) return null

        return query.split("&")
            .mapNotNull { parameter ->
                val key = parameter.substringBefore("=")
                val value = parameter.substringAfter("=", missingDelimiterValue = "")
                if (key == name) value else null
            }
            .firstOrNull()
    }
}
