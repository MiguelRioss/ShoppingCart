package productdatabaseaccesslayer

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.doubleOrNull
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
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val cacheFile: Path = Path.of(".cache", "product-catalog", "products-all.json")
) : ProductDataAccess {
    private var allProductsCache: String? = null
    private var productElementsCache: List<JsonElement>? = null
    private var productsByIdCache: Map<Long, JsonElement>? = null

    /**
     * Fetches all products from the external catalog, reusing the in-memory response after the first call.
     *
     * @return raw JSON response body
     */
    @Synchronized
    fun getAllProducts(): String =
        allProductsCache ?: getAllProductsFromNetworkOrDisk().also { allProductsCache = it }

    /**
     * Fetches products that include the purchase data required by cart saving.
     */
    fun getPurchasableProducts(): String =
        buildJsonArray {
            productElements()
                .filter { it.isPurchasable }
                .forEach { add(it) }
        }.toCompactJson()

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
     * Fetches a single product by id from the cached external catalog list.
     */
    override fun getProductById(productId: Long): String? = productsById()[productId]
        ?.toCompactJson()

    @Synchronized
    private fun productElements(): List<JsonElement> =
        productElementsCache ?: Json.parseToJsonElement(getAllProducts())
            .jsonArray
            .toList()
            .also { productElementsCache = it }

    @Synchronized
    private fun productsById(): Map<Long, JsonElement> =
        productsByIdCache ?: productElements()
            .mapNotNull { product ->
                val productId = product.jsonObject["id"]?.jsonPrimitive?.longOrNull
                productId?.let { it to product }
            }
            .toMap()
            .also { productsByIdCache = it }

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

    private fun getAllProductsFromNetworkOrDisk(): String =
        runCatching {
            get("products-all").also { writeProductsCache(it) }
        }.getOrElse { networkError ->
            readProductsCache()
                ?: throw IllegalStateException(
                    "Product catalog request failed and no local cache is available",
                    networkError
                )
        }

    private fun writeProductsCache(productsJson: String) {
        Files.createDirectories(cacheFile.parent)
        Files.writeString(cacheFile, productsJson, StandardCharsets.UTF_8)
    }

    private fun readProductsCache(): String? =
        if (Files.exists(cacheFile)) {
            Files.readString(cacheFile, StandardCharsets.UTF_8)
        } else {
            null
        }

    private companion object {
        const val BASE_URL = "https://cms.deferranti.com/wp-json/custom/v1"
    }

    private fun JsonElement.toCompactJson(): String = Json.encodeToString(JsonElement.serializer(), this)

    private val JsonElement.isPurchasable: Boolean
        get() {
            val product = jsonObject
            val purchaseInformation = product["purchase_information"] as? JsonObject
            val order = purchaseInformation?.get("order") as? JsonObject
                ?: return false

            return product["for_sale"]?.jsonPrimitive?.booleanOrNull == true &&
                (order["m2_per_box"]?.jsonPrimitive?.doubleOrNull ?: 0.0) > 0.0 &&
                (order["client_price_per_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0) > 0.0
        }
}
