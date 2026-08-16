package http.product

import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import productdatabaseaccesslayer.ProductCatalogDataSource

/**
 * Handles GET /products/:id requests by returning one external catalog product.
 */
class GetProductByIdHandler(
    private val productCatalogDataSource: ProductCatalogDataSource
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val productId = request.pathParameter("id")?.toLongOrNull()
            ?: return HttpError.InvalidJsonRequestBody.toResponse("Product id must be a number")

        val product = runCatching {
            productCatalogDataSource.getProductById(productId)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse(
                it.message ?: "Product catalog request failed"
            )
        } ?: return HttpError.NotFound.toResponse("Product not found")

        return HttpResponse(200, product)
    }
}
