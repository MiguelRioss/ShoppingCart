package services.shipping.fedex.ratequotation

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import services.shipping.fedex.FedExConfiguration
import services.shipping.fedex.auth.FedExAuthClient

class FedExRateHttpClient(
    private val configuration: FedExConfiguration,
    private val authClient: FedExAuthClient,
    private val requestMapper: FedExRateRequestMapper,
    private val httpClient: HttpClient =
        HttpClient.newHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) : FedExRateQuotationClient {

    override fun quote(
        request: FedExQuoteRequest
    ): JsonObject {
        val accessToken =
            authClient.accessToken()

        val rateUrl =
            "${configuration.baseUrl}/rate/v1/rates/quotes"

        println(
            "FedEx rate URL: $rateUrl"
        )

        println(
            "FedEx rate token check: " +
                    accessToken.take(6) +
                    "..." +
                    accessToken.takeLast(6)
        )

        val requestBody =
            requestMapper
                .map(request)
                .toString()

        println("FedEx rate request:")
        println(requestBody)

        val httpRequest =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        rateUrl
                    )
                )
                .header(
                    "Authorization",
                    "Bearer $accessToken"
                )
                .header(
                    "Content-Type",
                    "application/json"
                )
                .header(
                    "Accept",
                    "application/json"
                )
                .header(
                    "Accept-Encoding",
                    "gzip"
                )
                .POST(
                    HttpRequest.BodyPublishers
                        .ofString(requestBody)
                )
                .build()

        val response =
            httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers
                    .ofByteArray()
            )

        val responseBody =
            decodeResponseBody(response)

        println(
            "FedEx HTTP status: ${response.statusCode()}"
        )

        println(
            "FedEx Content-Encoding: ${
                response.headers()
                    .firstValue("Content-Encoding")
                    .orElse("none")
            }"
        )

        println("FedEx response:")
        println(responseBody)

        if (
            response.statusCode()
            !in 200..299
        ) {
            if (
                response.statusCode() == 401 &&
                responseBody.contains("NOT.AUTHORIZED.ERROR")
            ) {
                throw FedExRateAuthorizationException(
                    "FedEx rate request was not authorized. " +
                            "OAuth succeeded, but the token/account is not authorized for the Rate API request. " +
                            "Check that FEDEX_ACCOUNT_NUMBER belongs to the same FedEx Developer Portal project " +
                            "as FEDEX_CLIENT_ID/FEDEX_CLIENT_SECRET and that the project has Rate API access. " +
                            "FedEx response: $responseBody"
                )
            }

            error(
                "FedEx rate request failed " +
                        "with status " +
                        "${response.statusCode()}: " +
                        responseBody
            )
        }

        return json
            .parseToJsonElement(
                responseBody
            )
            .jsonObject
    }

    private fun decodeResponseBody(
        response: HttpResponse<ByteArray>
    ): String {
        val encoding =
            response.headers()
                .firstValue("Content-Encoding")
                .orElse("")
                .lowercase()

        val bytes =
            response.body()

        return when (encoding) {
            "gzip" ->
                GZIPInputStream(
                    ByteArrayInputStream(bytes)
                ).bufferedReader(
                    StandardCharsets.UTF_8
                ).use {
                    it.readText()
                }

            "",
            "identity" ->
                String(
                    bytes,
                    StandardCharsets.UTF_8
                )

            else ->
                error(
                    "Unsupported FedEx response " +
                            "Content-Encoding: $encoding"
                )
        }
    }
}
