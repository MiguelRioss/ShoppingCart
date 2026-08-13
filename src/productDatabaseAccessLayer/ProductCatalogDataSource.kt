package productdatabaseaccesslayer

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * HTTP-backed data source for the external De Ferranti product catalog.
 *
 * @param httpClient Java HTTP client used to call the external catalog API
 */
class ProductCatalogDataSource(
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : ProductDataAccess {
    /**
     * Fetches all products from the external catalog.
     *
     * @return raw JSON response body
     */
    fun getAllProducts(): String = get("products-all")

    /**
     * Fetches a single product by slug.
     *
     * @param productSlug URL slug for the product
     * @return raw JSON response body for the product
     */
    fun getProductBySlug(productSlug: String): String {
        val encodedSlug = URLEncoder.encode(productSlug, StandardCharsets.UTF_8)
            .replace("+", "%20")

        return get("product-by-slug/$encodedSlug")
    }

    /**
     * Fetches a single product by id from the external catalog list.
     */
    override fun getProductById(productId: Long): String? = Json.parseToJsonElement(getAllProducts())
        .jsonArray
        .firstOrNull { product -> product.jsonObject["id"]?.jsonPrimitive?.longOrNull == productId }
        ?.toCompactJson()

    /**
     * Performs a GET request against the external catalog.
     *
     * @param path API path appended to [BASE_URL]
     * @return raw response body
     * @throws IllegalStateException when the external API returns a non-2xx status
     */
    private fun get(path: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL/$path"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Product catalog request failed with HTTP ${response.statusCode()}"
        }

        return response.body()
    }

    private companion object {
        const val BASE_URL = "https://cms.deferranti.com/wp-json/custom/v1"
    }

    private fun JsonElement.toCompactJson(): String = Json.encodeToString(JsonElement.serializer(), this)
}
