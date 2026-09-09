package services.shipping.fedex.auth

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class JavaFedExHttpClient(
    private val httpClient: HttpClient =
        HttpClient.newHttpClient()
) : FedExHttpClient {

    override fun postForm(
        url: String,
        body: String
    ): FedExHttpResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(
                "Content-Type",
                "application/x-www-form-urlencoded"
            )
            .POST(
                HttpRequest.BodyPublishers.ofString(body)
            )
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        )

        return FedExHttpResponse(
            statusCode = response.statusCode(),
            body = response.body()
        )
    }
}