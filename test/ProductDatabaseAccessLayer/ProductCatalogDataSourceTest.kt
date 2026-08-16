package productdatabaseaccesslayer

import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.Certificate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductCatalogDataSourceTest {
    @Test
    fun `caches all products response for product id lookups`() {
        val httpClient = FakeHttpClient(
            """
            [
              {
                "id": 9278,
                "title": "Cached Product",
                "for_sale": true,
                "purchase_information": {
                  "order": {
                    "m2_per_box": "0.5",
                    "client_price_per_m2": "150"
                  }
                }
              }
            ]
            """.trimIndent()
        )
        val dataSource = ProductCatalogDataSource(httpClient, Files.createTempFile("products-cache", ".json"))

        dataSource.getAllProducts()
        val product = dataSource.getProductById(9278)

        assertNotNull(product)
        assertEquals(1, httpClient.sendCount)
    }

    @Test
    fun `returns only purchasable products from cache`() {
        val httpClient = FakeHttpClient(
            """
            [
              {
                "id": 1866,
                "title": "Display Only Product",
                "for_sale": false,
                "purchase_information": null
              },
              {
                "id": 9278,
                "title": "Purchasable Product",
                "for_sale": true,
                "purchase_information": {
                  "order": {
                    "m2_per_box": "0.5",
                    "client_price_per_m2": "150"
                  }
                }
              }
            ]
            """.trimIndent()
        )
        val dataSource = ProductCatalogDataSource(httpClient, Files.createTempFile("products-cache", ".json"))

        val products = dataSource.getPurchasableProducts()
        val product = dataSource.getProductById(9278)

        assertTrue(products.contains("\"id\":9278"))
        assertFalse(products.contains("\"id\":1866"))
        assertNotNull(product)
        assertEquals(1, httpClient.sendCount)
    }

    @Test
    fun `writes products response to disk cache after first fetch`() {
        val productsJson = """
            [
              {
                "id": 9278,
                "title": "Cached Product",
                "for_sale": true,
                "purchase_information": {
                  "order": {
                    "m2_per_box": "0.5",
                    "client_price_per_m2": "150"
                  }
                }
              }
            ]
        """.trimIndent()
        val cacheFile = Files.createTempFile("products-cache", ".json")
        Files.deleteIfExists(cacheFile)
        val dataSource = ProductCatalogDataSource(FakeHttpClient(productsJson), cacheFile)

        dataSource.getAllProducts()

        assertEquals(productsJson, Files.readString(cacheFile))
    }

    @Test
    fun `uses disk cache when product catalog request fails`() {
        val cacheFile = Files.createTempFile("products-cache", ".json")
        Files.writeString(
            cacheFile,
            """
            [
              {
                "id": 9278,
                "title": "Disk Cached Product",
                "for_sale": true,
                "purchase_information": {
                  "order": {
                    "m2_per_box": "0.5",
                    "client_price_per_m2": "150"
                  }
                }
              }
            ]
            """.trimIndent()
        )
        val dataSource = ProductCatalogDataSource(FailingHttpClient(), cacheFile)

        val product = dataSource.getProductById(9278)

        assertNotNull(product)
        assertTrue(product.contains("Disk Cached Product"))
    }

    private class FakeHttpClient(
        private val responseBody: String
    ) : HttpClient() {
        var sendCount = 0

        override fun <T : Any?> send(
            request: HttpRequest,
            responseBodyHandler: HttpResponse.BodyHandler<T>
        ): HttpResponse<T> {
            sendCount += 1
            @Suppress("UNCHECKED_CAST")
            return FakeHttpResponse(request, responseBody as T)
        }

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            responseBodyHandler: HttpResponse.BodyHandler<T>
        ): CompletableFuture<HttpResponse<T>> =
            CompletableFuture.completedFuture(send(request, responseBodyHandler))

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            responseBodyHandler: HttpResponse.BodyHandler<T>,
            pushPromiseHandler: HttpResponse.PushPromiseHandler<T>
        ): CompletableFuture<HttpResponse<T>> =
            CompletableFuture.completedFuture(send(request, responseBodyHandler))

        override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
        override fun connectTimeout(): Optional<Duration> = Optional.empty()
        override fun followRedirects(): Redirect = Redirect.NEVER
        override fun proxy(): Optional<ProxySelector> = Optional.empty()
        override fun sslContext(): SSLContext = SSLContext.getDefault()
        override fun sslParameters(): SSLParameters = SSLParameters()
        override fun authenticator(): Optional<java.net.Authenticator> = Optional.empty()
        override fun version(): Version = Version.HTTP_2
        override fun executor(): Optional<Executor> = Optional.empty()
    }

    private class FailingHttpClient : HttpClient() {
        override fun <T : Any?> send(
            request: HttpRequest,
            responseBodyHandler: HttpResponse.BodyHandler<T>
        ): HttpResponse<T> {
            throw java.io.IOException("network unavailable")
        }

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            responseBodyHandler: HttpResponse.BodyHandler<T>
        ): CompletableFuture<HttpResponse<T>> {
            val future = CompletableFuture<HttpResponse<T>>()
            future.completeExceptionally(java.io.IOException("network unavailable"))
            return future
        }

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            responseBodyHandler: HttpResponse.BodyHandler<T>,
            pushPromiseHandler: HttpResponse.PushPromiseHandler<T>
        ): CompletableFuture<HttpResponse<T>> = sendAsync(request, responseBodyHandler)

        override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
        override fun connectTimeout(): Optional<Duration> = Optional.empty()
        override fun followRedirects(): Redirect = Redirect.NEVER
        override fun proxy(): Optional<ProxySelector> = Optional.empty()
        override fun sslContext(): SSLContext = SSLContext.getDefault()
        override fun sslParameters(): SSLParameters = SSLParameters()
        override fun authenticator(): Optional<java.net.Authenticator> = Optional.empty()
        override fun version(): Version = Version.HTTP_2
        override fun executor(): Optional<Executor> = Optional.empty()
    }

    private class FakeHttpResponse<T>(
        private val request: HttpRequest,
        private val body: T
    ) : HttpResponse<T> {
        override fun statusCode(): Int = 200
        override fun request(): HttpRequest = request
        override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()
        override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }
        override fun body(): T = body
        override fun sslSession(): Optional<SSLSession> = Optional.empty()
        override fun uri(): URI = request.uri()
        override fun version(): HttpClient.Version = HttpClient.Version.HTTP_2
    }
}
