package services.shipping.fedex.availability

import domain.shipping.ShippingAddress
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import services.shipping.fedex.FedExConfiguration
import services.shipping.fedex.auth.FedExAuthClient

class FedExServiceAvailabilityHttpClient(
    private val configuration: FedExConfiguration,
    private val authClient: FedExAuthClient,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) : FedExServiceAvailabilityClient {

    override fun packageAndServiceOptions(
        request: FedExServiceAvailabilityRequest
    ): FedExServiceAvailabilityResponse {
        val accessToken = authClient.accessToken()
        val url = "${configuration.baseUrl}/availability/v1/packageandserviceoptions"
        val body = mapRequest(request).toString()

        println("FedEx availability URL: $url")
        println(
            "FedEx availability token check: " +
                    accessToken.take(6) +
                    "..." +
                    accessToken.takeLast(6)
        )
        println("FedEx availability request:")
        println(body)

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(
            httpRequest,
            HttpResponse.BodyHandlers.ofByteArray()
        )
        val responseBody = decodeResponseBody(response)

        println("FedEx availability HTTP status: ${response.statusCode()}")
        println(
            "FedEx availability Content-Encoding: " +
                    response.headers()
                        .firstValue("Content-Encoding")
                        .orElse("none")
        )
        println("FedEx availability response:")
        println(responseBody)

        if (response.statusCode() !in 200..299) {
            error(
                "FedEx service availability failed " +
                        "with status ${response.statusCode()}: " +
                        responseBody
            )
        }

        return FedExServiceAvailabilityResponse(
            options = parseOptions(responseBody),
            rawResponse = responseBody
        )
    }

    private fun mapRequest(
        request: FedExServiceAvailabilityRequest
    ): JsonObject =
        buildJsonObject {
            putJsonObject("accountNumber") {
                put("value", configuration.accountNumber)
            }
            put("shipDate", LocalDate.now().plusDays(1).toString())
            putJsonObject("requestedShipment") {
                putJsonObject("shipper") {
                    put("address", addressJson(request.originAddress))
                }
                putJsonArray("recipients") {
                    add(
                        buildJsonObject {
                            put("address", addressJson(request.destinationAddress))
                        }
                    )
                }
                putJsonArray("requestedPackageLineItems") {
                    add(
                        buildJsonObject {
                            putJsonObject("weight") {
                                put("units", "KG")
                                put("value", 1.0)
                            }
                        }
                    )
                }
                request.customsDetail?.let { customs ->
                    putJsonObject("customsClearanceDetail") {
                        putJsonArray("commodities") {
                            add(
                                buildJsonObject {
                                    put("description", customs.commodityDescription)
                                    put("countryOfManufacture", customs.countryOfManufacture)
                                    put("quantity", customs.quantity)
                                    put("quantityUnits", customs.quantityUnits)
                                    putJsonObject("customsValue") {
                                        put("amount", customs.customsValue)
                                        put("currency", customs.currency)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            putJsonArray("carrierCodes") {
                add("FDXE")
            }
        }

    private fun addressJson(
        address: ShippingAddress
    ): JsonObject =
        buildJsonObject {
            putJsonArray("streetLines") {
                address.streetLines.forEach {
                    add(it)
                }
            }
            put("city", address.city)
            put("postalCode", address.postalCode)
            put("countryCode", address.countryCode.toFedExCountryCode())
        }

    private fun parseOptions(
        responseBody: String
    ): List<FedExServicePackageOption> {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val output = root["output"]?.jsonObject ?: return emptyList()
        return output.findServicePackageOptions().distinct()
    }

    private fun JsonElement.findServicePackageOptions(): List<FedExServicePackageOption> {
        val options = mutableListOf<FedExServicePackageOption>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val serviceType = element["serviceType"]
                        ?.keyValue()
                    val packagingType = (
                            element["packagingType"]
                                ?: element["packageType"]
                            )
                        ?.keyValue()

                    if (serviceType != null && packagingType != null) {
                        options += FedExServicePackageOption(
                            serviceType = serviceType,
                            packagingType = packagingType
                        )
                    }

                    element.values.forEach(::visit)
                }

                is JsonArray ->
                    element.forEach(::visit)

                else ->
                    Unit
            }
        }

        visit(this)
        return options
    }

    private fun JsonElement.keyValue(): String? =
        when (this) {
            is JsonObject ->
                this["key"]
                    ?.jsonPrimitive
                    ?.content

            else ->
                jsonPrimitive.content
        }

    private fun decodeResponseBody(
        response: HttpResponse<ByteArray>
    ): String {
        val encoding = response.headers()
            .firstValue("Content-Encoding")
            .orElse("")
            .lowercase()

        val bytes = response.body()

        return when (encoding) {
            "gzip" ->
                GZIPInputStream(
                    ByteArrayInputStream(bytes)
                ).bufferedReader(StandardCharsets.UTF_8).use {
                    it.readText()
                }

            "",
            "identity" ->
                String(bytes, StandardCharsets.UTF_8)

            else ->
                error("Unsupported FedEx response Content-Encoding: $encoding")
        }
    }

    private fun String.toFedExCountryCode(): String =
        when (trim().uppercase()) {
            "PORTUGAL",
            "PT" ->
                "PT"

            "UNITED KINGDOM",
            "UK",
            "GB" ->
                "GB"

            else ->
                trim().uppercase()
        }
}
