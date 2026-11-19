package services.shipping

import services.shipping.rates.FedExDutyTaxEstimator

import config.Environment
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentDimensions
import domain.shipment.ShipmentPackage
import domain.shipment.ShipmentWeight
import java.math.BigDecimal
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class FedExDutyTaxEstimatorSmokeTest {
    @Test
    fun `production FedEx estimates DDP charges without booking a shipment`() {
        assumeTrue(System.getenv("RUN_FEDEX_DDP_ESTIMATE_SMOKE") == "true")
        assumeTrue(Environment.get("FEDEX_MODE") == "PRODUCTION")
        val estimate = FedExDutyTaxEstimator().estimate(
            origin = ShipmentAddress("Rua da Vidoeira 1", city = "Albergaria dos Doze", postalCode = "3100-097", countryCode = "PT"),
            destination = ShipmentAddress("1 Test Street", city = "New York", postalCode = "10001", countryCode = "US"),
            packages = listOf(ShipmentPackage(
                "Handmade glazed ceramic tiles", "PT", 4, "PCS",
                ShipmentWeight(40.0, "KG"), ShipmentDimensions(80, 60, 47, "CM"), 360.0, "EUR"
            )),
            shippingCost = BigDecimal("40.00"),
            serviceType = "INTERNATIONAL_PRIORITY"
        )
        assertTrue(estimate >= BigDecimal.ZERO)
    }
}
