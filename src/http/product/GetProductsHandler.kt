package http.product

import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import productdatabaseaccesslayer.ProductDataAccess

/**
 * Handles GET /products requests by returning the external catalog response.
 */
class GetProductsHandler(
    private val productDataAccess: ProductDataAccess
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse =
        runCatching {
            HttpResponse(200, productDataAccess.getPurchasableProducts())
        }.getOrElse {
            HttpError.InvalidJsonRequestBody.toResponse(
                it.message ?: "Product catalog request failed"
            )
        }
}

