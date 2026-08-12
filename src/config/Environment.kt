package config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads values from real environment variables first, then from a local .env file.
 */
object Environment {
    private val dotenvValues: Map<String, String> by lazy { loadDotenv() }

    fun get(name: String): String? =
        System.getenv(name) ?: dotenvValues[name]

    private fun loadDotenv(): Map<String, String> {
        val path = Path.of(".env")
        if (!Files.exists(path)) return emptyMap()

        return Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf("=")
                if (separatorIndex <= 0) return@mapNotNull null

                val key = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim().trimMatchingQuotes()

                key to value
            }
            .toMap()
    }

    private fun String.trimMatchingQuotes(): String {
        if (length < 2) return this

        val first = first()
        val last = last()

        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }
}
