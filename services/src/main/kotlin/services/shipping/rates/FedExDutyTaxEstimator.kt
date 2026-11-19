package services.shipping.rates

import config.ShippingMode
import config.ShippingProviderConfig
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentPackage
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface DutyTaxEstimator {
    fun estimate(
        origin: ShipmentAddress,
        destination: ShipmentAddress,
        packages: List<ShipmentPackage>,
        shippingCost: BigDecimal,
        serviceType: String
    ): BigDecimal
}

class FedExDutyTaxEstimator(
    private val configForOrigin: (String) -> ShippingProviderConfig = { country -> ShippingProviderConfig.load(country) },
    private val client: HttpClient = HttpClient.newHttpClient()
) : DutyTaxEstimator {
    override fun estimate(
        origin: ShipmentAddress,
        destination: ShipmentAddress,
        packages: List<ShipmentPackage>,
        shippingCost: BigDecimal,
        serviceType: String
    ): BigDecimal {
        val originCountry = when (origin.countryCode.trim().uppercase()) {
            "PT" -> "PT"
            "GB", "UK" -> "GB"
            else -> error("Unsupported FedEx billing account origin: ${origin.countryCode}")
        }
        val config = configForOrigin(originCountry)
        require(config.mode != ShippingMode.OFFLINE) { "FedEx Duty and Tax API requires an online account" }
        val credentials = requireNotNull(config.credentials)
        val baseUrl = if (config.mode == ShippingMode.PRODUCTION) "https://apis.fedex.com" else "https://apis-sandbox.fedex.com"
        val tokenForm = listOf(
            "grant_type" to "client_credentials",
            "client_id" to credentials.clientId,
            "client_secret" to credentials.clientSecret
        ).joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        val tokenResponse = client.send(
            HttpRequest.newBuilder(URI.create("$baseUrl/oauth/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenForm))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        check(tokenResponse.statusCode() == 200) { "FedEx authentication failed: ${tokenResponse.statusCode()}" }
        val token = Json.parseToJsonElement(tokenResponse.body()).jsonObject.getValue("access_token").jsonPrimitive.content
        val body = buildJsonObject {
            put("shipper", buildJsonObject { put("countryCode", JsonPrimitive(origin.countryCode)) })
            put("recipient", buildJsonObject {
                put("countryCode", JsonPrimitive(destination.countryCode))
                destination.stateOrProvince?.let { put("stateOrProvinceCode", JsonPrimitive(it)) }
            })
            put("shippingCost", buildJsonObject {
                put("amount", JsonPrimitive(shippingCost))
                put("currencyCode", JsonPrimitive("EUR"))
            })
            put("serviceType", JsonPrimitive(serviceType))
            put("commodities", buildJsonArray {
                packages.forEach { item ->
                    require(item.quantity > 0 && item.customsValue >= 0) { "Invalid customs value or quantity" }
                    add(buildJsonObject {
                        put("description", JsonPrimitive(item.description))
                        put("quantity", JsonPrimitive(item.quantity))
                        put("countryOfManufacture", JsonPrimitive(item.countryOfManufacture))
                        put("unitPrice", buildJsonObject {
                            put("amount", JsonPrimitive(BigDecimal.valueOf(item.customsValue).divide(BigDecimal(item.quantity))))
                            put("currencyCode", JsonPrimitive(item.customsCurrency))
                        })
                    })
                }
            })
        }
        val response = client.send(
            HttpRequest.newBuilder(URI.create("$baseUrl/dutytax/v1/estimate"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("X-locale", "en_US")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        check(response.statusCode() == 200) { "FedEx duty and tax estimate failed: ${response.statusCode()}" }
        val details = Json.parseToJsonElement(response.body()).jsonObject
            .getValue("output").jsonObject.getValue("edtDetails").jsonArray
        require(details.isNotEmpty()) { "FedEx returned no duty and tax estimate" }
        return details.sumOf { detail ->
            val summary = detail.jsonObject.getValue("summary").jsonObject
            require(summary.getValue("currency").jsonPrimitive.content.equals("EUR", true)) {
                "FedEx duty and tax estimate is not in EUR"
            }
            listOf("totalDuties", "totalTaxes", "totalFees").sumOf { field ->
                summary.getValue(field).jsonPrimitive.content.toBigDecimal()
            }
        }
    }
}
