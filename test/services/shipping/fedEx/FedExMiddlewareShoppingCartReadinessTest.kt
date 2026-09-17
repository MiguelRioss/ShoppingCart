package services.shipping

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductCatalogDataSource
import shipment.core.ProviderAccount
import shipment.core.ProviderCredentials
import shipment.core.ShipmentAddress
import shipment.core.ShipmentDimensions
import shipment.core.ShipmentPackage
import shipment.core.ShipmentProviderFactory
import shipment.core.ShipmentProviderType
import shipment.core.ShipmentWeight
import org.junit.jupiter.api.Assumptions.assumeTrue

class ShipmentProviderRealApiTest {

    @Test
    fun `Azure Tide MC52 gets real shipping quotes through shipment provider`() {
        assumeTrue(
            System.getenv("RUN_REAL_FEDEX_TESTS") == "true",
            "Set RUN_REAL_FEDEX_TESTS=true to call the real FedEx shipment provider"
        )

        println()
        println("==============================================")
        println("REAL SHIPMENT PROVIDER TEST")
        println("==============================================")

        val env =
            loadEnv()

        /*
         * ----------------------------------------------------------
         * 1. CREATE THE REAL SHIPMENT PROVIDER
         * ----------------------------------------------------------
         */

        val provider =
            ShipmentProviderFactory.create(
                type = ShipmentProviderType.FEDEX,
                credentials =
                    ProviderCredentials(
                        clientId =
                            requireNotNull(
                                env["FEDEX_CLIENT_ID"]
                            ) {
                                "FEDEX_CLIENT_ID missing from .env"
                            },
                        clientSecret =
                            requireNotNull(
                                env["FEDEX_CLIENT_SECRET"]
                            ) {
                                "FEDEX_CLIENT_SECRET missing from .env"
                            }
                    ),
                account =
                    ProviderAccount(
                        accountNumber =
                            requireNotNull(
                                env["FEDEX_ACCOUNT_NUMBER"]
                            ) {
                                "FEDEX_ACCOUNT_NUMBER missing from .env"
                            },
                        countryCode = requireNotNull(
                            env["FEDEX_COUNTRY_CODE"]
                        )
                    )
            )

        /*
         * ----------------------------------------------------------
         * 2. LOAD REAL PRODUCT FROM DE FERRANTI API
         * ----------------------------------------------------------
         */

        val productId = 9278L

        val requestedSquareMeters =
            BigDecimal("0.5")

        val productCatalog =
            ProductCatalogDataSource()

        println()
        println("Loading product $productId from De Ferranti API...")

        val rawProduct =
            requireNotNull(
                productCatalog.getProductDetailsById(productId)
            ) {
                "Product $productId could not be loaded"
            }

        val product =
            Json.parseToJsonElement(rawProduct)
                .jsonObject

        val title =
            requireString(
                product = product,
                key = "title"
            )

        println()
        println("PRODUCT")
        println("----------------------------------------------")
        println("ID:       $productId")
        println("Title:    $title")
        println("Quantity: $requestedSquareMeters m2")

        /*
         * ----------------------------------------------------------
         * 3. READ SUPPLIER COLLECTION ADDRESS
         * ----------------------------------------------------------
         */

        val supplier =
            requireNotNull(
                product["supplier"]
            ) {
                "Product has no supplier data"
            }.jsonObject

        val collectionPostCode =
            requireString(
                product = supplier,
                key = "post_code_collection"
            )

        val collectionCountry =
            requireString(
                product = supplier,
                key = "country_collection"
            )

        val collectionCountryCode =
            countryNameToIso2(
                collectionCountry
            )

        val from =
            ShipmentAddress(
                postalCode = collectionPostCode,
                countryCode = collectionCountryCode
            )

        println()
        println("COLLECTION ADDRESS")
        println("----------------------------------------------")
        println("Postcode: $collectionPostCode")
        println("Country:  $collectionCountry")
        println("ISO code: $collectionCountryCode")

        /*
         * ----------------------------------------------------------
         * 4. READ PRODUCT PURCHASE / SHIPPING DATA
         * ----------------------------------------------------------
         */

        val purchaseInformation =
            requireNotNull(
                product["purchase_information"]
            ) {
                "Product has no purchase_information"
            }.jsonObject

        val order =
            requireNotNull(
                purchaseInformation["order"]
            ) {
                "Product has no order data"
            }.jsonObject

        val taxFreightAndCustoms =
            requireNotNull(
                purchaseInformation["tax_freight_and_customs"]
            ) {
                "Product has no tax_freight_and_customs data"
            }.jsonObject

        val shipping =
            requireNotNull(
                purchaseInformation["shipping"]
            ) {
                "Product has no shipping data"
            }.jsonObject

        /*
         * ----------------------------------------------------------
         * 5. FIND THE CORRECT PACKING ROW
         *
         * We ask for 0.5 m2.
         *
         * The code finds quantity = 0.5 in packing_size.
         *
         * Nothing is hardcoded regarding dimensions or weight.
         * ----------------------------------------------------------
         */

        val packingRows =
            requireNotNull(
                shipping["packing_size"]
            ) {
                "Product has no packing_size data"
            }.jsonArray

        val packing =
            packingRows
                .map { it.jsonObject }
                .firstOrNull { packingRow ->

                    val packingQuantity =
                        BigDecimal(
                            requireString(
                                product = packingRow,
                                key = "quantity"
                            )
                        )

                    packingQuantity.compareTo(
                        requestedSquareMeters
                    ) == 0
                }
                ?: error(
                    "No packing configuration found for " +
                            "$requestedSquareMeters m2"
                )

        val length =
            requireString(
                product = packing,
                key = "length"
            ).toInt()

        val width =
            requireString(
                product = packing,
                key = "width"
            ).toInt()

        val height =
            requireString(
                product = packing,
                key = "height"
            ).toInt()

        val packedWeight =
            requireString(
                product = packing,
                key = "packed_weight"
            ).toDouble()

        val packaging =
            requireString(
                product = packing,
                key = "packaging"
            )

        println()
        println("PACKING ROW SELECTED FROM API")
        println("----------------------------------------------")
        println("Quantity:   $requestedSquareMeters m2")
        println("Packaging:  $packaging")
        println("Weight:     $packedWeight KG")
        println("Dimensions: $length x $width x $height CM")

        /*
         * These assertions prove that the API row we selected
         * is the correct Azure Tide 0.5 m2 row.
         */

        assertEquals(
            8.2,
            packedWeight
        )

        assertEquals(
            30,
            length
        )

        assertEquals(
            30,
            width
        )

        assertEquals(
            20,
            height
        )

        /*
         * ----------------------------------------------------------
         * 6. READ CUSTOMS INFORMATION
         * ----------------------------------------------------------
         */

        val customsDescription =
            requireString(
                product = taxFreightAndCustoms,
                key = "customs_description"
            )

        val countryOfOrigin =
            requireString(
                product = taxFreightAndCustoms,
                key = "country_of_origin"
            )

        val countryOfOriginCode =
            countryNameToIso2(
                countryOfOrigin
            )

        val pricePerSquareMeter =
            BigDecimal(
                requireString(
                    product = order,
                    key = "client_price_per_m2"
                )
            )

        val customsValue =
            pricePerSquareMeter
                .multiply(
                    requestedSquareMeters
                )

        println()
        println("CUSTOMS")
        println("----------------------------------------------")
        println("Description: $customsDescription")
        println("Origin:      $countryOfOrigin")
        println("Origin code: $countryOfOriginCode")
        println("Value:       $customsValue EUR")

        /*
         * ----------------------------------------------------------
         * 7. BUILD THE GENERIC SHIPMENT PACKAGE
         * ----------------------------------------------------------
         */

        val shipmentPackage =
            ShipmentPackage(
                description = customsDescription,
                countryOfManufacture = countryOfOriginCode,
                quantity = 1,
                quantityUnits = "PCS",
                weight =
                    ShipmentWeight(
                        value = packedWeight,
                        units = "KG"
                    ),
                dimensions =
                    ShipmentDimensions(
                        length = length,
                        width = width,
                        height = height,
                        units = "CM"
                    ),
                customsValue =
                    customsValue.toDouble(),
                customsCurrency = "EUR",
                preferredCurrency = "EUR"
            )

        /*
         * Destination is test input.
         *
         * Origin comes from the product.
         */
        val to =
            ShipmentAddress(
                postalCode = "8000-339",
                countryCode = "PT"
            )

        println()
        println("==============================================")
        println("SHIPMENT PACKAGE SENT TO PROVIDER")
        println("==============================================")
        println("FROM")
        println("${from.postalCode} ${from.countryCode}")

        println()
        println("TO")
        println("${to.postalCode} ${to.countryCode}")

        println()
        println("DESCRIPTION")
        println(shipmentPackage.description)

        println()
        println("WEIGHT")
        println(
            "${shipmentPackage.weight.value} " +
                    shipmentPackage.weight.units
        )

        println()
        println("DIMENSIONS")
        println(
            "${shipmentPackage.dimensions.length} x " +
                    "${shipmentPackage.dimensions.width} x " +
                    "${shipmentPackage.dimensions.height} " +
                    shipmentPackage.dimensions.units
        )

        println()
        println("CUSTOMS VALUE")
        println(
            "${shipmentPackage.customsValue} " +
                    shipmentPackage.customsCurrency
        )

        /*
         * ----------------------------------------------------------
         * 8. CALL THE REAL MIDDLEWARE
         * ----------------------------------------------------------
         */

        println()
        println("==============================================")
        println("CALLING SHIPMENT PROVIDER")
        println("==============================================")
        println()

        val quotes =
            provider.getQuotes(
                from = from,
                to = to,
                product = shipmentPackage
            )

        /*
         * ----------------------------------------------------------
         * 9. PRINT EVERY OPTION RETURNED
         * ----------------------------------------------------------
         */

        println()
        println("==============================================")
        println("SHIPPING OPTIONS RETURNED: ${quotes.size}")
        println("==============================================")

        quotes.forEachIndexed { index, quote ->

            println()
            println("OPTION ${index + 1}")
            println("----------------------------------------------")
            println("Provider: ${quote.providerType}")
            println("Service:  ${quote.serviceName}")
            println("Type:     ${quote.serviceType}")
            println(
                "Price:    ${quote.amount} ${quote.currency}"
            )
            println(
                "Delivery: ${quote.estimatedDeliveryDate}"
            )
        }

        assertTrue(
            quotes.isNotEmpty(),
            "Shipment provider returned no quotes"
        )

        /*
         * ----------------------------------------------------------
         * 10. SHOW CHEAPEST OPTION
         * ----------------------------------------------------------
         */

        val cheapest =
            quotes.minByOrNull {
                it.amount
            }

        requireNotNull(cheapest)

        println()
        println("==============================================")
        println("CHEAPEST OPTION")
        println("==============================================")
        println("Provider: ${cheapest.providerType}")
        println("Service:  ${cheapest.serviceName}")
        println("Type:     ${cheapest.serviceType}")
        println(
            "Price:    ${cheapest.amount} ${cheapest.currency}"
        )
        println(
            "Delivery: ${cheapest.estimatedDeliveryDate}"
        )
        println("==============================================")
    }

    private fun requireString(
        product: JsonObject,
        key: String
    ): String =
        requireNotNull(
            product[key]
        ) {
            "Missing required field: $key"
        }
            .jsonPrimitive
            .content

    private fun countryNameToIso2(
        countryName: String
    ): String {

        val match =
            Locale.getISOCountries()
                .asSequence()
                .map { countryCode ->

                    val locale =
                        Locale.Builder()
                            .setRegion(countryCode)
                            .build()

                    countryCode to
                            locale.getDisplayCountry(
                                Locale.ENGLISH
                            )
                }
                .firstOrNull { (_, displayCountry) ->

                    displayCountry.equals(
                        countryName,
                        ignoreCase = true
                    )
                }

        return requireNotNull(match) {
            "Could not convert country '$countryName' to ISO country code"
        }.first
    }

    private fun loadEnv(): Map<String, String> {

        val envFile =
            Path.of(".env")

        require(
            Files.exists(envFile)
        ) {
            ".env file not found at ${envFile.toAbsolutePath()}"
        }

        return Files
            .readAllLines(envFile)
            .map {
                it.trim()
            }
            .filter {
                it.isNotEmpty()
            }
            .filterNot {
                it.startsWith("#")
            }
            .mapNotNull { line ->

                val separatorIndex =
                    line.indexOf('=')

                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }

                val key =
                    line
                        .substring(
                            0,
                            separatorIndex
                        )
                        .trim()

                val value =
                    line
                        .substring(
                            separatorIndex + 1
                        )
                        .trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("'")

                key to value
            }
            .toMap()
    }
}
