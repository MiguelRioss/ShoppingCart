package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import io.github.cdimascio.dotenv.dotenv
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlin.test.assertTrue
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import services.shipping.fedex.FedExConfiguration
import services.shipping.fedex.auth.FedExAuthClient
import services.shipping.fedex.auth.JavaFedExHttpClient

class FedExPortugalSandboxDiagnosticTest {
    @Test
    fun `compare domestic payloads and international control with the same OAuth token`() {
        val env = dotenv { directory = "./"; filename = ".env" }
        val configuration = FedExConfiguration(
            baseUrl = env["FEDEX_BASE_URL"] ?: "https://apis-sandbox.fedex.com",
            clientId = requireNotNull(env["FEDEX_CLIENT_ID"]),
            clientSecret = requireNotNull(env["FEDEX_CLIENT_SECRET"]),
            accountNumber = requireNotNull(env["FEDEX_ACCOUNT_NUMBER"])
        )
        require(configuration.baseUrl == "https://apis-sandbox.fedex.com") {
            "This diagnostic uses sandbox test shipments only"
        }
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        val token = FedExAuthClient(configuration, JavaFedExHttpClient(client)).accessToken()
        val mapper = FedExRateRequestMapper(configuration.accountNumber)
        val domestic = mapper.map(domesticRequest())
        val minimal = JsonObject(domestic - "rateRequestControlParameters").shipment {
            remove("shipDateStamp")
            remove("packagingType")
            put("pickupType", JsonPrimitive("DROPOFF_AT_FEDEX_LOCATION"))
            put("rateRequestType", JsonArray(listOf(JsonPrimitive("ACCOUNT"), JsonPrimitive("LIST"))))
            put("requestedPackageLineItems", buildJsonArray {
                add(buildJsonObject { putJsonObject("weight") { put("units", "KG"); put("value", 1) } })
            })
        }
        val international = mapper.map(domesticRequest().copy(
            destinationAddress = ShippingAddress(listOf("221B Baker Street"), "London", "NW1 6XE", "GB"),
            customsDetail = FedExCustomsDetail(
                "Ceramic tile sample", "PT", "690721", 1, "PCS",
                BigDecimal("10.00"), BigDecimal("10.00"), "EUR"
            )
        ))
        val cases = linkedMapOf(
            "PT-GB control" to international,
            "PT-PT current mapper" to domestic,
            "PT-PT no transit times" to JsonObject(domestic - "rateRequestControlParameters"),
            "PT-PT official minimal structure" to minimal,
            "PT-PT postal only" to minimal.shipment {
                for (party in listOf("shipper", "recipient")) {
                    val address = getValue(party).jsonObject.getValue("address").jsonObject
                    put(party, buildJsonObject { put("address", JsonObject(address.filterKeys {
                        it in setOf("postalCode", "countryCode")
                    })) })
                }
            },
            "PT-PT scheduled pickup" to minimal.shipment { put("pickupType", JsonPrimitive("USE_SCHEDULED_PICKUP")) },
            "PT-PT contact pickup" to minimal.shipment { put("pickupType", JsonPrimitive("CONTACT_FEDEX_TO_SCHEDULE")) },
            "PT-PT counts currency carrier" to JsonObject(minimal.shipment {
                put("preferredCurrency", JsonPrimitive("EUR"))
                put("totalPackageCount", JsonPrimitive(1))
                put("shipDateStamp", JsonPrimitive(LocalDate.now().plusDays(1).toString()))
            } + ("carrierCodes" to JsonArray(listOf(JsonPrimitive("FDXE")))))
        )
        for (service in listOf("FEDEX_PRIORITY", "FEDEX_PRIORITY_EXPRESS", "FEDEX_FIRST")) {
            cases["PT-PT $service"] = minimal.shipment {
                put("serviceType", JsonPrimitive(service))
                put("packagingType", JsonPrimitive("YOUR_PACKAGING"))
            }
        }
        val postalOnly = cases.getValue("PT-PT postal only")
        for (service in listOf("FEDEX_PRIORITY", "FEDEX_PRIORITY_EXPRESS", "FEDEX_FIRST")) {
            for (packaging in listOf("YOUR_PACKAGING", "FEDEX_PAK")) {
                cases["PT-PT postal $service $packaging"] = postalOnly.shipment {
                    put("serviceType", JsonPrimitive(service))
                    put("packagingType", JsonPrimitive(packaging))
                }
            }
        }
        for (format in listOf("no hyphen", "four digits")) {
            cases["PT-PT postal $format FEDEX_PRIORITY"] = minimal.shipment {
                put("serviceType", JsonPrimitive("FEDEX_PRIORITY"))
                put("packagingType", JsonPrimitive("YOUR_PACKAGING"))
                for (party in listOf("shipper", "recipient")) {
                    val address = getValue(party).jsonObject.getValue("address").jsonObject
                    val postal = address.getValue("postalCode").jsonPrimitive.content
                    put(party, buildJsonObject {
                        put("address", JsonObject(address + ("postalCode" to JsonPrimitive(
                            if (format == "four digits") postal.take(4) else postal.replace("-", "")
                        ))))
                    })
                }
            }
        }
        cases["PT-PT UK test account FEDEX_PRIORITY"] = JsonObject(
            cases.getValue("PT-PT FEDEX_PRIORITY") + ("accountNumber" to buildJsonObject { put("value", "802255209") })
        )
        val records = mutableListOf<JsonObject>()
        var domesticQuoteReceived = false
        try {
            for (address in listOf(domesticRequest().originAddress, domesticRequest().destinationAddress)) {
                val payload = buildJsonObject {
                    put("carrierCode", "FDXE")
                    put("countryCode", address.countryCode)
                    put("postalCode", address.postalCode)
                    put("shipDate", LocalDate.now().plusDays(1).toString())
                }
                val response = client.send(
                    HttpRequest.newBuilder(URI.create("${configuration.baseUrl}/country/v1/postal/validate"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                )
                val body = decode(response)
                println("Postal validation ${address.city} ${address.postalCode}: HTTP=${response.statusCode()} $body")
                records += buildJsonObject {
                    put("case", "postal validation ${address.city}")
                    put("status", response.statusCode())
                    put("request", payload)
                    put("response", Json.parseToJsonElement(body))
                }
            }
            for ((name, payload) in cases.filterKeys { env["FEDEX_DIAGNOSTIC_FILTER"]?.toRegex()?.containsMatchIn(it) != false }) {
                for (attempt in 1..3) {
                    val response = client.send(
                        HttpRequest.newBuilder(URI.create("${configuration.baseUrl}/rate/v1/rates/quotes"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer $token")
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .header("x-locale", "en_US")
                            .header("x-customer-transaction-id", UUID.randomUUID().toString())
                            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                            .build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    )
                    val responseText = decode(response)
                    val body = Json.parseToJsonElement(responseText).jsonObject
                    val virtual = (body["output"]?.jsonObject?.get("alerts") as? JsonArray).orEmpty().any {
                        it.jsonObject["code"]?.jsonPrimitive?.content == "VIRTUAL.RESPONSE"
                    }
                    val rates = body["output"]?.jsonObject?.get("rateReplyDetails") as? JsonArray
                    val hasQuote = rates.orEmpty().any { rate ->
                        (rate.jsonObject["ratedShipmentDetails"] as? JsonArray).orEmpty().any {
                            (it.jsonObject["totalNetCharge"] as? JsonPrimitive)
                                ?.content?.toBigDecimalOrNull()?.signum() == 1
                        }
                    }
                    val codes = (body["errors"] as? JsonArray).orEmpty().map {
                        it.jsonObject["code"]?.jsonPrimitive?.content
                    }
                    println("$name attempt=$attempt HTTP=${response.statusCode()} quote=$hasQuote virtual=$virtual errors=$codes")
                    records += buildJsonObject {
                        put("case", name)
                        put("attempt", attempt)
                        put("status", response.statusCode())
                        put("request", payload)
                        put("response", body)
                    }
                    if (response.statusCode() == 200 && hasQuote) {
                        if (name.startsWith("PT-PT") && !virtual && rates.orEmpty().any {
                            it.jsonObject["serviceType"]?.jsonPrimitive?.content in
                                setOf("FEDEX_PRIORITY", "FEDEX_PRIORITY_EXPRESS", "FEDEX_FIRST")
                        }) domesticQuoteReceived = true
                        println("Quoted services: ${rates?.map { it.jsonObject["serviceType"] }}")
                        break
                    }
                    if (response.statusCode() !in setOf(401, 429, 500, 502, 503, 504)) break
                    if (attempt < 3) Thread.sleep(1_000L * attempt)
                }
            }
        } finally {
            val report = Path.of("build/reports/fedex/portugal-diagnostic-${System.currentTimeMillis()}.json")
            Files.createDirectories(report.parent)
            Files.writeString(report, Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(records)))
            println("FedEx diagnostic report: ${report.toAbsolutePath()}")
        }
        assertTrue(domesticQuoteReceived, "No non-virtual PT -> PT domestic quotation was returned. See the diagnostic report.")
    }

    private fun decode(response: HttpResponse<ByteArray>): String =
        if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true)) {
            GZIPInputStream(response.body().inputStream()).bufferedReader().use { it.readText() }
        } else {
            response.body().toString(Charsets.UTF_8)
        }

    private fun JsonObject.shipment(edit: MutableMap<String, JsonElement>.() -> Unit): JsonObject =
        JsonObject(this + ("requestedShipment" to JsonObject(getValue("requestedShipment").jsonObject.toMutableMap().apply(edit))))

    private fun domesticRequest() = FedExQuoteRequest(
        // Published addresses, used only for rate requests; no shipment is created.
        // https://www.visitlisboa.com/pt-pt/locais/hotel-vincci-baixa
        originAddress = ShippingAddress(listOf("Rua do Comercio 32-38"), "Lisboa", "1100-150", "PT"),
        // https://www.maison-albar-hotels-le-monumental-palace.com/pt/page/contacto.510.html
        destinationAddress = ShippingAddress(listOf("Avenida dos Aliados 151"), "Porto", "4000-067", "PT"),
        packageSize = ShippingPackageSize(
            BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, null, "Box(s)"
        ),
        incoterm = null,
        customsDetail = null,
        shipDate = LocalDate.now().plusDays(1),
        packagingType = "YOUR_PACKAGING"
    )
}
