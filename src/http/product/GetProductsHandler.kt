package http.product

import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import productdatabaseaccesslayer.ProductCatalogDataSource

/**
 * Handles GET /products requests by returning the external catalog response.
 */
class GetProductsHandler(
    private val productCatalogDataSource: ProductCatalogDataSource
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse =
        runCatching {
            HttpResponse(200, productCatalogDataSource.getPurchasableProducts())
        }.getOrElse {
            HttpError.InvalidJsonRequestBody.toResponse(
                it.message ?: "Product catalog request failed"
            )
        }
}
