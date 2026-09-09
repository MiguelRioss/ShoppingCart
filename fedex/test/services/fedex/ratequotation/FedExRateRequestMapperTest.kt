package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FedExRateRequestMapperTest {

    private val mapper = FedExRateRequestMapper(
        accountNumber = "account-number"
    )

    @Test
    fun `map accepts ISO country codes outside explicit aliases`() {
        val payload = mapper.map(
            quoteRequest(
                destinationCountryCode = "CA",
                manufactureCountryCode = "nl"
            )
        )

        val shipment = payload["requestedShipment"] as JsonObject
        val recipient = shipment["recipient"] as JsonObject
        val address = recipient["address"] as JsonObject
        val customs = shipment["customsClearanceDetail"] as JsonObject
        val commodities = customs["commodities"] as JsonArray
        val commodity = commodities.first() as JsonObject

        assertEquals(
            "CA",
            (address["countryCode"] as JsonPrimitive).content
        )
        assertEquals(
            "NL",
            (commodity["countryOfManufacture"] as JsonPrimitive).content
        )
    }

    @Test
    fun `map rejects non ISO country text`() {
        assertThrows(IllegalStateException::class.java) {
            mapper.map(
                quoteRequest(
                    destinationCountryCode = "Neverland"
                )
            )
        }
    }

    private fun quoteRequest(
        destinationCountryCode: String,
        manufactureCountryCode: String = "PT"
    ) =
        FedExQuoteRequest(
            originAddress = ShippingAddress(
                streetLines = listOf("Rua do Comercio 10"),
                city = "Lisbon",
                postalCode = "1100-150",
                countryCode = "PT"
            ),
            destinationAddress = ShippingAddress(
                streetLines = listOf("1 Main Street"),
                city = "Toronto",
                postalCode = "M5H 2N2",
                countryCode = destinationCountryCode
            ),
            packageSize = ShippingPackageSize(
                quantity = BigDecimal("1.0"),
                length = BigDecimal("10"),
                width = BigDecimal("10"),
                height = BigDecimal("10"),
                packedWeight = BigDecimal("1.0"),
                packagingCost = null,
                packaging = null
            ),
            incoterm = null,
            customsDetail = FedExCustomsDetail(
                commodityDescription = "Ceramic tile sample",
                countryOfManufacture = manufactureCountryCode,
                harmonizedCode = "690721",
                quantity = 1,
                quantityUnits = "PCS",
                unitPrice = BigDecimal("10.00"),
                customsValue = BigDecimal("10.00"),
                currency = "EUR"
            )
        )
}