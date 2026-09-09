package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import io.github.cdimascio.dotenv.dotenv
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import services.shipping.fedex.FedExConfiguration
import services.shipping.fedex.availability.FedExServiceAvailabilityHttpClient
import services.shipping.fedex.availability.FedExServiceAvailabilityRequest
import services.shipping.fedex.availability.RetryingFedExServiceAvailabilityClient
import services.shipping.fedex.auth.FedExAuthClient
import services.shipping.fedex.auth.JavaFedExHttpClient

class FedExPortugalDomesticRateIntegrationTest {

    @Test
    fun `real FedEx sandbox rates available Portugal domestic services`() {
        val availability = retryingFedExServiceAvailabilityClient(
            accountNumber = "740561073",
            maxAttempts = 6
        ).packageAndServiceOptions(
            FedExServiceAvailabilityRequest(
                originAddress = portugalOriginAddress,
                destinationAddress = portugalDestinationAddress
            )
        )

        println("FedEx Portugal domestic service availability options:")
        availability.options.forEach {
            println("${it.serviceType} / ${it.packagingType}")
        }

        val possibleServices = mutableListOf<String>()
        val shipDate = LocalDate.now().plusDays(1)
        val serviceOptions = availability.options.ifEmpty {
            priorityServiceTypes.map {
                services.shipping.fedex.availability.FedExServicePackageOption(
                    serviceType = it,
                    packagingType = "YOUR_PACKAGING"
                )
            }
        }

        serviceOptions.forEach { option ->
            rateVariants.forEach { variant ->
                println(
                    "Checking FedEx Portugal domestic service: " +
                            "${option.serviceType} / ${option.packagingType} / " +
                            "${variant.pickupType} / ${variant.rateRequestTypes.joinToString("+")}"
                )

                val result = runCatching {
                    retryingFedExRateClient(
                        accountNumber = "740561073",
                        maxAttempts = 4
                    ).quote(
                        portugalDomesticRequest(
                            serviceType = option.serviceType,
                            packagingType = option.packagingType,
                            pickupType = variant.pickupType,
                            rateRequestTypes = variant.rateRequestTypes,
                            shipDate = shipDate
                        )
                    )
                }

                result
                    .onSuccess { response ->
                        val rates = response["output"]?.jsonObject?.get("rateReplyDetails") as? JsonArray
                        check(rates.orEmpty().any { rate ->
                            (rate.jsonObject["ratedShipmentDetails"] as? JsonArray).orEmpty().any {
                                it.jsonObject["totalNetCharge"]?.jsonPrimitive?.content
                                    ?.toBigDecimalOrNull()?.signum() == 1
                            }
                        }) { "FedEx returned a response without a priced quotation" }
                        possibleServices += "${option.serviceType} / ${option.packagingType} / " +
                                "${variant.pickupType} / ${variant.rateRequestTypes.joinToString("+")}"
                        println("POSSIBLE: ${possibleServices.last()}")
                        println(response)
                    }
                    .onFailure { exception ->
                        println(
                            "NO QUOTE RECEIVED: ${option.serviceType} / ${option.packagingType} / " +
                                    "${variant.pickupType} / ${variant.rateRequestTypes.joinToString("+")}"
                        )
                        println(exception.message)
                    }
            }
        }

        println("Possible FedEx Portugal domestic services:")
        possibleServices.forEach {
            println(it)
        }

        println("Checked ${serviceOptions.size} FedEx Portugal domestic service options")
        assertTrue(possibleServices.isNotEmpty(), "FedEx did not return any PT -> PT priced quotation")
    }

    private val priorityServiceTypes = listOf(
        "FEDEX_FIRST",
        "FEDEX_PRIORITY_EXPRESS",
        "FEDEX_PRIORITY",
        "FEDEX_ECONOMY_SELECT",
        "FEDEX_REGIONAL_ECONOMY",
        "FEDEX_INTERNATIONAL_CONNECT_PLUS",
        "INTERNATIONAL_FIRST",
        "FEDEX_INTERNATIONAL_PRIORITY",
        "FEDEX_INTERNATIONAL_PRIORITY_EXPRESS"
    )

    private fun portugalDomesticRequest(
        serviceType: String,
        packagingType: String,
        pickupType: String,
        rateRequestTypes: List<String>,
        shipDate: LocalDate
    ): FedExQuoteRequest =
        FedExQuoteRequest(
            originAddress = portugalOriginAddress,
            destinationAddress = portugalDestinationAddress,
            packageSize = ShippingPackageSize(
                quantity = BigDecimal("1.0"),
                length = BigDecimal("10"),
                width = BigDecimal("10"),
                height = BigDecimal("10"),
                packedWeight = BigDecimal("1.0"),
                packagingCost = null,
                packaging = "Box(s)"
            ),
            incoterm = null,
            customsDetail = null,
            rateRequestTypes = rateRequestTypes,
            serviceType = serviceType,
            packagingType = packagingType,
            pickupType = pickupType,
            shipDate = shipDate,
            includeDimensions = packagingType == "YOUR_PACKAGING"
        )

    private val rateVariants = listOf(
        RateVariant(
            pickupType = "USE_SCHEDULED_PICKUP",
            rateRequestTypes = listOf("LIST")
        ),
        RateVariant(
            pickupType = "USE_SCHEDULED_PICKUP",
            rateRequestTypes = listOf("ACCOUNT")
        )
    )

    private data class RateVariant(
        val pickupType: String,
        val rateRequestTypes: List<String>
    )

    private val portugalOriginAddress = ShippingAddress(
        streetLines = listOf("Rua do Comercio 32-38"),
        city = "Lisboa",
        postalCode = "1100-150",
        countryCode = "PT"
    )

    private val portugalDestinationAddress = ShippingAddress(
        streetLines = listOf("Avenida dos Aliados 151"),
        city = "Porto",
        postalCode = "4000-067",
        countryCode = "PT"
    )

    private fun fedExRateClient(
        accountNumber: String? = null
    ): FedExRateHttpClient {
        val dotenv = dotenv {
            directory = "./"
            filename = ".env"
        }

        val configuration = FedExConfiguration(
            baseUrl = dotenv["FEDEX_BASE_URL"] ?: "https://apis-sandbox.fedex.com",
            clientId = requireNotNull(dotenv["FEDEX_CLIENT_ID"]) {
                "FEDEX_CLIENT_ID is missing from .env"
            },
            clientSecret = requireNotNull(dotenv["FEDEX_CLIENT_SECRET"]) {
                "FEDEX_CLIENT_SECRET is missing from .env"
            },
            accountNumber = accountNumber
                ?: requireNotNull(dotenv["FEDEX_ACCOUNT_NUMBER"]) {
                    "FEDEX_ACCOUNT_NUMBER is missing from .env"
                }
        )

        return FedExRateHttpClient(
            configuration = configuration,
            authClient = FedExAuthClient(
                configuration = configuration,
                httpClient = JavaFedExHttpClient()
            ),
            requestMapper = FedExRateRequestMapper(
                accountNumber = configuration.accountNumber
            )
        )
    }

    private fun retryingFedExRateClient(
        accountNumber: String? = null,
        maxAttempts: Int? = null
    ): FedExRateQuotationClient =
        RetryingFedExRateQuotationClient(
            delegate = fedExRateClient(accountNumber),
            maxAttempts = maxAttempts
        )

    private fun retryingFedExServiceAvailabilityClient(
        accountNumber: String? = null,
        maxAttempts: Int? = null
    ) =
        RetryingFedExServiceAvailabilityClient(
            delegate = fedExServiceAvailabilityClient(accountNumber),
            maxAttempts = maxAttempts
        )

    private fun fedExServiceAvailabilityClient(
        accountNumber: String? = null
    ): FedExServiceAvailabilityHttpClient {
        val dotenv = dotenv {
            directory = "./"
            filename = ".env"
        }

        val configuration = FedExConfiguration(
            baseUrl = dotenv["FEDEX_BASE_URL"] ?: "https://apis-sandbox.fedex.com",
            clientId = requireNotNull(dotenv["FEDEX_CLIENT_ID"]) {
                "FEDEX_CLIENT_ID is missing from .env"
            },
            clientSecret = requireNotNull(dotenv["FEDEX_CLIENT_SECRET"]) {
                "FEDEX_CLIENT_SECRET is missing from .env"
            },
            accountNumber = accountNumber
                ?: requireNotNull(dotenv["FEDEX_ACCOUNT_NUMBER"]) {
                    "FEDEX_ACCOUNT_NUMBER is missing from .env"
                }
        )

        return FedExServiceAvailabilityHttpClient(
            configuration = configuration,
            authClient = FedExAuthClient(
                configuration = configuration,
                httpClient = JavaFedExHttpClient()
            )
        )
    }
}
