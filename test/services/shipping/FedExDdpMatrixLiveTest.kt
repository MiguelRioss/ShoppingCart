package services.shipping

import config.Environment
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentDimensions
import domain.shipment.ShipmentPackage
import domain.shipment.ShipmentRateRequest
import domain.shipment.ShipmentWeight
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test

class FedExDdpMatrixLiveTest {
    @Test
    fun `collect production transport and DDP estimates without booking shipments`() {
        assumeTrue(System.getenv("RUN_FEDEX_DDP_MATRIX") == "true")
        assumeTrue(Environment.get("FEDEX_MODE") == "PRODUCTION")
        val origin = ShipmentAddress("Rua da Vidoeira 1", city = "Albergaria dos Doze", postalCode = "3100-097", countryCode = "PT")
        val packages = listOf(ShipmentPackage(
            "Handmade glazed ceramic tiles", "PT", 4, "PCS",
            ShipmentWeight(40.0, "KG"), ShipmentDimensions(80, 60, 47, "CM"), 360.0, "EUR"
        ))
        val destinations = listOf(
            Triple("Portugal", "PT", "1000-001"),
            Triple("Italia", "IT", "20121"),
            Triple("Espanha", "ES", "28001"),
            Triple("Franca", "FR", "75001"),
            Triple("Alemanha", "DE", "10115"),
            Triple("Paises Baixos", "NL", "1012 AB"),
            Triple("Belgica", "BE", "1000"),
            Triple("Irlanda", "IE", "D02 X285"),
            Triple("Suecia", "SE", "111 22"),
            Triple("Estados Unidos", "US", "10001"),
            Triple("Canada", "CA", "M5H 2N2"),
            Triple("Suica", "CH", "8001"),
            Triple("Noruega", "NO", "0150"),
            Triple("Australia", "AU", "2000"),
            Triple("Japao", "JP", "100-0001"),
            Triple("Emirados Arabes Unidos", "AE", "00000")
        )
        val cities = mapOf(
            "PT" to "Lisboa", "IT" to "Milano", "ES" to "Madrid", "FR" to "Paris",
            "DE" to "Berlin", "NL" to "Amsterdam", "BE" to "Brussels", "IE" to "Dublin",
            "SE" to "Stockholm", "US" to "New York", "CA" to "Toronto", "CH" to "Zurich",
            "NO" to "Oslo", "AU" to "Sydney", "JP" to "Tokyo", "AE" to "Dubai"
        )
        val euCountries = setOf("PT", "IT", "ES", "FR", "DE", "NL", "BE", "IE", "SE")
        val carrier = AccountRoutingShippingProvider()
        val dutyEstimator = FedExDutyTaxEstimator()
        val rows = buildJsonArray {
            destinations.forEach { (name, country, postalCode) ->
                val destination = ShipmentAddress("1 Test Street", city = cities.getValue(country), postalCode = postalCode, countryCode = country,
                    stateOrProvince = if (country == "CA") "ON" else null)
                val row = runCatching {
                    val quote = carrier.getQuotes(ShipmentRateRequest(origin, destination, packages, "EUR"))
                        .filter { it.currency.equals("EUR", true) }
                        .minByOrNull { it.amount }
                        ?: error("No EUR transport quote")
                    val importCharges = if (country in euCountries) BigDecimal.ZERO else
                        dutyEstimator.estimate(origin, destination, packages, BigDecimal.valueOf(quote.amount), quote.serviceType)
                    buildJsonObject {
                        put("country", JsonPrimitive(country))
                        put("destination", JsonPrimitive(name))
                        put("postalCode", JsonPrimitive(postalCode))
                        put("service", JsonPrimitive(quote.serviceType))
                        put("transportEUR", JsonPrimitive(quote.amount))
                        put("importChargesEUR", JsonPrimitive(importCharges))
                        put("status", JsonPrimitive("OK"))
                    }
                }.getOrElse { failure ->
                    buildJsonObject {
                        put("country", JsonPrimitive(country))
                        put("destination", JsonPrimitive(name))
                        put("postalCode", JsonPrimitive(postalCode))
                        put("status", JsonPrimitive("ERROR"))
                        put("reason", JsonPrimitive(failure.message?.take(180) ?: failure.javaClass.simpleName))
                    }
                }
                add(row)
            }
        }
        val output = Path.of("outputs", "fedex-ddp-matrix.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, rows.toString())
        println("FedEx matrix: ${rows.size} destinations; saved $output")
    }
}
