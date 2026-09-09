package services.shipping.fedex.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import services.shipping.fedex.FedExConfiguration

class FedExAuthClient(
    private val configuration: FedExConfiguration,
    private val httpClient: FedExHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) {

    fun accessToken(): String {
        val body = formBody(
            "grant_type" to "client_credentials",
            "client_id" to configuration.clientId,
            "client_secret" to configuration.clientSecret
        )

        val response = httpClient.postForm(
            url = "${configuration.baseUrl}/oauth/token",
            body = body
        )

        if (response.statusCode !in 200..299) {
            error(
                "FedEx authentication failed " +
                        "with status ${response.statusCode}: " +
                        response.body
            )
        }

        val responseJson = json
            .parseToJsonElement(response.body)
            .jsonObject
        println(
            "FedEx OAuth token_type: " +
                    responseJson["token_type"]?.jsonPrimitive?.content
        )

        println(
            "FedEx OAuth expires_in: " +
                    responseJson["expires_in"]?.jsonPrimitive?.content
        )

        println(
            "FedEx OAuth scope: " +
                    responseJson["scope"]?.jsonPrimitive?.content
        )
        val token =
            responseJson["access_token"]
                ?.jsonPrimitive
                ?.content
                ?: error(
                    "FedEx authentication response " +
                            "did not contain access_token"
                )

        println(
            "FedEx token suffix: " +
                    token.takeLast(6)
        )

        return token
    }

    private fun formBody(
        vararg values: Pair<String, String>
    ): String =
        values.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }

    private fun encode(value: String): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        )
}