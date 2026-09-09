package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import domain.shipping.ShippingQuotation
import io.github.cdimascio.dotenv.dotenv
import java.io.File
import java.io.FileInputStream
import java.math.BigDecimal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.fedex.ratequotation.FedExQuotationExcelWriter
import services.shipping.ProductShippingReader
import services.shipping.fedex.FedExConfiguration
import services.shipping.fedex.auth.FedExAuthClient
import services.shipping.fedex.auth.JavaFedExHttpClient

class FedExAllCountriesIntegrationTest {

    @Test
    fun `FedEx quotations for all configured countries`() {
        runQuotationSweep(
            destinations =
                FedExDestinationCatalogue
                    .destinations,
            outputFile =
                defaultOutputFile(),
            title =
                "FEDEx COUNTRY SWEEP COMPLETE"
        )
    }

    @Test
    fun `FedEx quotations for failed countries from previous Excel`() {
        val destinations =
            failedDestinationsFrom(
                defaultOutputFile()
            )

        println()
        println(
            "Failed countries found in previous Excel: " +
                    destinations.size
        )

        runQuotationSweep(
            destinations =
                destinations,
            outputFile =
                File(
                    "build/reports/" +
                            "fedex-country-quotation-rerun-failed-results.xlsx"
                ),
            title =
                "FEDEx FAILED COUNTRY RERUN COMPLETE"
        )
    }

    private fun runQuotationSweep(
        destinations: List<FedExCountryDestination>,
        outputFile: File,
        title: String
    ) {
        val runner =
            FedExCountryQuotationRunner(
                rateClientFactory = {
                    fedExRateClient(
                        accountNumber =
                            "740561073"
                    )
                },
                maxTransientAttempts =
                    10,
                retryDelayMillis =
                    1_500
            )

        val results =
            destinations.mapIndexed {
                    index,
                    destination ->

                println()
                println(
                    "========================================"
                )

                println(
                    "Country ${index + 1} of " +
                            "${destinations.size}"
                )

                println(
                    "${destination.countryName} " +
                            "(${destination.countryCode})"
                )

                println(
                    "========================================"
                )

                val result =
                    quoteUntilResolvedWithManualFallback(
                        runner =
                            runner,
                        destination =
                            destination
                    )

                println(
                    "Result: " +
                            if (result.success) {
                                "QUOTATION RECEIVED"
                            } else {
                                "NO QUOTATION"
                            }
                )

                result
            }

        FedExQuotationExcelWriter()
            .write(
                results =
                    results,
                outputFile =
                    outputFile
            )

        println()
        println(
            "========================================"
        )

        println(
            title
        )

        println(
            "========================================"
        )

        println(
            "Countries tested: " +
                    results.size
        )

        println(
            "Quotations received: " +
                    results.count {
                        it.success
                    }
        )

        println(
            "No quotation: " +
                    results.count {
                        !it.success
                    }
        )

        println()
        println(
            "Excel:"
        )

        println(
            outputFile.absolutePath
        )

        assertTrue(
            results.isNotEmpty()
        )
    }

    private fun failedDestinationsFrom(
        outputFile: File
    ): List<FedExCountryDestination> {
        require(outputFile.exists()) {
            "Previous FedEx Excel file does not exist: " +
                    outputFile.absolutePath
        }

        val failedCountryCodes =
            FileInputStream(outputFile).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet =
                        workbook.getSheetAt(0)

                    val header =
                        sheet.getRow(0)

                    val countryCodeColumn =
                        header.columnIndex("Country Code")

                    val quotationAvailableColumn =
                        header.columnIndex("Quotation Available")

                    (1..sheet.lastRowNum)
                        .mapNotNull { rowIndex ->
                            val row =
                                sheet.getRow(rowIndex)
                                    ?: return@mapNotNull null

                            val quotationAvailable =
                                row.getCell(quotationAvailableColumn)
                                    ?.stringCellValue
                                    ?.trim()
                                    ?.uppercase()

                            if (quotationAvailable != "NO") {
                                return@mapNotNull null
                            }

                            row.getCell(countryCodeColumn)
                                ?.stringCellValue
                                ?.trim()
                                ?.uppercase()
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                        }
                        .toSet()
                }
            }

        return FedExDestinationCatalogue
            .destinations
            .filter {
                it.countryCode.uppercase() in failedCountryCodes
            }
    }

    private fun Row.columnIndex(
        heading: String
    ): Int {
        for (index in firstCellNum until lastCellNum) {
            if (getCell(index)?.stringCellValue == heading) {
                return index
            }
        }

        error(
            "Column not found in previous Excel: $heading"
        )
    }

    private fun defaultOutputFile(): File =
        File(
            "build/reports/" +
                    "fedex-country-quotation-results.xlsx"
        )

    private fun quoteUntilResolvedWithManualFallback(
        runner: FedExCountryQuotationRunner,
        destination: FedExCountryDestination
    ): FedExCountryQuotationResult {
        val result =
            runner.quoteUntilResolved(
                destination =
                    destination,
                requestFactory = {
                    createQuotationRequest(it)
                }
            )

        if (result.success || destination.countryCode != "PT") {
            return result
        }

        val manualPrice =
            manualPortugalDomesticFallbackPrice()
                ?: return result

        println(
            "Using manual MC52 Portugal domestic FedEx fallback: " +
                    "${manualPrice.toPlainString()} EUR"
        )

        return result.copy(
            success =
                true,
            response =
                manualPortugalDomesticFallbackResponse(
                    manualPrice
                ).toString(),
            error =
                "FedEx API did not return a Portugal domestic quote " +
                        "after retries; manual fallback price was used. " +
                        "Last FedEx error: ${result.error.orEmpty()}"
        )
    }

    private fun manualPortugalDomesticFallbackPrice(): BigDecimal? =
        (
                System.getProperty(
                    MANUAL_MC52_PT_PRICE_PROPERTY
                )
                    ?: System.getenv(
                        MANUAL_MC52_PT_PRICE_ENV
                    )
                )
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                BigDecimal(it)
            }

    private fun manualPortugalDomesticFallbackResponse(
        priceEur: BigDecimal
    ): JsonObject =
        buildJsonObject {
            put(
                "output",
                buildJsonObject {
                    putJsonArray("rateReplyDetails") {
                        add(
                            buildJsonObject {
                                put(
                                    "serviceType",
                                    "FEDEX_PRIORITY_MANUAL_FALLBACK"
                                )
                                put(
                                    "serviceName",
                                    "FedEx Priority - manual Portugal domestic fallback"
                                )
                                putJsonArray("ratedShipmentDetails") {
                                    add(
                                        buildJsonObject {
                                            put(
                                                "currency",
                                                "EUR"
                                            )
                                            put(
                                                "totalNetCharge",
                                                priceEur.toDouble()
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }

    private fun createQuotationRequest(
        destination: FedExCountryDestination
    ): FedExQuoteRequest {

        val international =
            destination.countryCode != "PT"

        val portugalDomestic =
            destination.countryCode == "PT"

        val productShippingReader =
            ProductShippingReader(
                ProductCatalogDataSource()
            )

        val quotation =
            ShippingQuotation(
                productId = MC52_PRODUCT_ID,
                quantityM2 = MC52_QUANTITY_M2,
                recipientAddress = destinationAddress(destination),
                destinationAddress = destinationAddress(destination),
                incoterm = null
            )

        return FedExQuoteRequest(
            originAddress =
                productShippingReader.collectionAddressFor(MC52_PRODUCT_ID),

            destinationAddress =
                quotation.destinationAddress,

            packageSize =
                productShippingReader.packageSizeFor(quotation),

            incoterm =
                null,

            customsDetail =
                if (international) {
                    FedExCustomsDetail(
                        commodityDescription =
                            "Ceramic tile sample",
                        countryOfManufacture =
                            "PT",
                        harmonizedCode =
                            "690721",
                        quantity =
                            1,
                        quantityUnits =
                            "PCS",
                        unitPrice =
                            BigDecimal("10.00"),
                        customsValue =
                            BigDecimal("10.00"),
                        currency =
                            "EUR"
                    )
                } else {
                    null
                },

            rateRequestTypes =
                listOf("LIST"),

            pickupType =
                "USE_SCHEDULED_PICKUP",

            includeDimensions =
                true,

            returnTransitTimes =
                true,

            serviceType =
                null,

            packagingType =
                if (portugalDomestic) {
                    "FEDEX_PAK"
                } else {
                    null
                },

            postalCodeOnlyAddresses =
                false
        )
    }

    private fun destinationAddress(
        destination: FedExCountryDestination
    ): ShippingAddress =
        ShippingAddress(
            streetLines =
                listOf(
                    destination.streetLine
                ),
            city =
                destination.city,
            postalCode =
                destination.postalCode,
            countryCode =
                destination.countryCode
        )

    private companion object {
        const val MC52_PRODUCT_ID = 9278L
        const val MANUAL_MC52_PT_PRICE_ENV =
            "FEDEX_MANUAL_MC52_PT_DOMESTIC_PRICE_EUR"
        const val MANUAL_MC52_PT_PRICE_PROPERTY =
            "fedex.manual.mc52.pt.domestic.price.eur"
        val MC52_QUANTITY_M2 = BigDecimal("0.5")
    }
    private fun fedExRateClient(
        accountNumber: String? = null
    ): FedExRateHttpClient {

        val dotenv =
            dotenv {
                directory = "./"
                filename = ".env"
            }

        val configuration =
            FedExConfiguration(
                baseUrl =
                    dotenv[
                        "FEDEX_BASE_URL"
                    ]
                        ?: "https://apis-sandbox.fedex.com",

                clientId =
                    requireNotNull(
                        dotenv[
                            "FEDEX_CLIENT_ID"
                        ]
                    ) {
                        "FEDEX_CLIENT_ID is missing from .env"
                    },

                clientSecret =
                    requireNotNull(
                        dotenv[
                            "FEDEX_CLIENT_SECRET"
                        ]
                    ) {
                        "FEDEX_CLIENT_SECRET is missing from .env"
                    },

                accountNumber =
                    accountNumber
                        ?: requireNotNull(
                            dotenv[
                                "FEDEX_ACCOUNT_NUMBER"
                            ]
                        ) {
                            "FEDEX_ACCOUNT_NUMBER is missing from .env"
                        }
            )

        return FedExRateHttpClient(
            configuration =
                configuration,

            authClient =
                FedExAuthClient(
                    configuration =
                        configuration,
                    httpClient =
                        JavaFedExHttpClient()
                ),

            requestMapper =
                FedExRateRequestMapper(
                    accountNumber =
                        configuration.accountNumber
                )
        )
    }
}
