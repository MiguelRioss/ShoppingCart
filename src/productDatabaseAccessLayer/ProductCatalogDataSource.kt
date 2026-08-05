package productdatabaseaccesslayer

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ProductCatalogDataSource(
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : ProductDataAccess {
    fun getAllProducts(): String = get("products-all")

    fun getProductBySlug(productSlug: String): String {
        val encodedSlug = URLEncoder.encode(productSlug, StandardCharsets.UTF_8)
            .replace("+", "%20")

        return get("product-by-slug/$encodedSlug")
    }

    override fun productExists(productId: Long): Boolean = Json.parseToJsonElement(getAllProducts())
        .jsonArray
        .any { product -> product.jsonObject["id"]?.jsonPrimitive?.longOrNull == productId }

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
}
