package services.shipping

import config.Environment
import domain.shipment.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

/** Explicit opt-in: requests a rate only, never books a shipment or creates a label. */
class FedExPtRateSmokeTest {
    @Test fun productionPtAccountReturnsEurRates() {
        assumeTrue(System.getenv("RUN_FEDEX_PT_RATE_SMOKE") == "true")
        assertEquals("PRODUCTION", Environment.get("FEDEX_MODE"))
        val request = ShipmentRateRequest(
            from = ShipmentAddress(addressLine1 = "Rua da Vidoeira 1", city = "Albergaria dos Doze", postalCode = "3100-097", countryCode = "PT"),
            to = ShipmentAddress(addressLine1 = "1 Rue de Rivoli", city = "Paris", postalCode = "75001", countryCode = "FR"),
            packages = listOf(ShipmentPackage("Ceramic tile sample", "PT", 1, "PCS", ShipmentWeight(1.0, "KG"), ShipmentDimensions(10, 10, 10, "CM"), 20.0, "EUR")),
            preferredCurrency = "EUR"
        )
        val quotes = try { AccountRoutingShippingProvider().getQuotes(request) } catch (e: error.FedExServiceException) { error("FedEx rate rejected: status=${e.statusCode}, details=${e.description}") }
        assertTrue(quotes.any { it.currency.equals("EUR", true) }, "No EUR quote returned: ${quotes.map { it.currency }.distinct()} (${quotes.size} quotes)")
        assertFalse(quotes.any { it.serviceName.contains("Offline", true) })
        println("Production PT rate check passed: ${quotes.size} carrier quotes returned.")
    }

    @Test fun productionUkAccountReturnsQuotesForUkOrigin() {
        assumeTrue(System.getenv("RUN_FEDEX_UK_RATE_SMOKE") == "true")
        assertEquals("PRODUCTION", Environment.get("FEDEX_MODE"))
        val request = ShipmentRateRequest(
            from = ShipmentAddress(addressLine1 = "10 Downing Street", city = "London", postalCode = "SW1A 2AA", countryCode = "GB"),
            to = ShipmentAddress(addressLine1 = "1 Rue de Rivoli", city = "Paris", postalCode = "75001", countryCode = "FR"),
            packages = listOf(ShipmentPackage("Ceramic tile sample", "PT", 1, "PCS", ShipmentWeight(1.0, "KG"), ShipmentDimensions(10, 10, 10, "CM"), 20.0, "EUR")),
            preferredCurrency = "GBP"
        )
        val quotes = try { AccountRoutingShippingProvider().getQuotes(request) } catch (e: error.FedExServiceException) { error("FedEx UK rate rejected: status=${e.statusCode}, details=${e.description}") }
        assertTrue(quotes.isNotEmpty(), "FedEx returned no UK-origin quotes")
        assertFalse(quotes.any { it.serviceName.contains("Offline", true) })
        println("Production UK rate check passed: ${quotes.size} carrier quotes returned.")
    }
}
