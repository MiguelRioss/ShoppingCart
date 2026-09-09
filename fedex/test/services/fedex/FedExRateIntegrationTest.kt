package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import io.github.cdimascio.dotenv.dotenv
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import services.shipping.fedex.FedExConfiguration
import services.shipping.fedex.auth.FedExAuthClient
import services.shipping.fedex.auth.JavaFedExHttpClient

class FedExRateIntegrationTest {

    @Test
    fun `real FedEx sandbox eventually returns a quotation`() {
        val response = retryingFedExRateClient(
            accountNumber = "740561073"
        ).quote(
            FedExQuoteRequest(
                originAddress = ShippingAddress(
                    streetLines = listOf("Rua do Comercio 10"),
                    city = "Lisbon",
                    postalCode = "1100-150",
                    countryCode = "PT"
                ),
                destinationAddress = ShippingAddress(
                    streetLines = listOf("221B Baker Street"),
                    city = "London",
                    postalCode = "NW1 6XE",
                    countryCode = "GB"
                ),
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
                customsDetail = FedExCustomsDetail(
                    commodityDescription = "Ceramic tile sample",
                    countryOfManufacture = "PT",
                    harmonizedCode = "690721",
                    quantity = 1,
                    quantityUnits = "PCS",
                    unitPrice = BigDecimal("10.00"),
                    customsValue = BigDecimal("10.00"),
                    currency = "EUR"
                )
            )
        )

        println("FedEx Portugal to United Kingdom quotation response:")
        println(response)

        assertTrue(response.isNotEmpty())
    }

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
        accountNumber: String? = null
    ): FedExRateQuotationClient =
        RetryingFedExRateQuotationClient(
            delegate = fedExRateClient(accountNumber),
            maxAttempts = null
        )
}
