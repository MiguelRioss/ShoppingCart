package services.shipping.fedex.auth

data class FedExHttpResponse(
    val statusCode: Int,
    val body: String
)

interface FedExHttpClient {
    fun postForm(
        url: String,
        body: String
    ): FedExHttpResponse
}