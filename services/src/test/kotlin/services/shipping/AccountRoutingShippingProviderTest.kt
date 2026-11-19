package services.shipping

import domain.shipment.*
import shipment.core.ShippingProvider
import kotlin.test.*

class AccountRoutingShippingProviderTest {
    @Test fun choosesFedExAccountOnlyFromShipmentOrigin() {
        val created = mutableListOf<String>()
        val calls = mutableListOf<String>()
        val router = AccountRoutingShippingProvider { country ->
            created += country
            object : ShippingProvider {
                override fun getQuotes(request: ShipmentRateRequest): List<ShipmentQuote> {
                    calls += country
                    return emptyList()
                }
                override fun prepareShipping(request: ShipmentRequest): Shipment = error("Not used")
            }
        }
        for ((from, to, expected) in listOf(
            Triple("PT","FR","PT"), Triple("GB","FR","GB"), Triple("PT","GB","PT"),
            Triple("GB","GB","GB"), Triple(" uk ","PT","GB")
        )) {
            router.getQuotes(ShipmentRateRequest(address(from), address(to), packages(from), "EUR"))
            assertEquals(expected, calls.last())
        }
        assertEquals(listOf("PT", "GB"), created)
    }
    @Test fun missingUkAccountFailsWithoutCallingPortugal() {
        val router = AccountRoutingShippingProvider { country -> error("Missing $country account") }
        val error = assertFailsWith<IllegalStateException> {
            router.getQuotes(ShipmentRateRequest(address("GB"), address("PT"), packages("GB"), "EUR"))
        }
        assertEquals("Missing GB account", error.message)
    }
    private fun packages(origin: String) = listOf(ShipmentPackage("Tiles", origin, 1, "PCS", ShipmentWeight(1.0, "KG"), ShipmentDimensions(10, 10, 10, "CM"), 20.0, "EUR"))
    private fun address(country: String) = ShipmentAddress(addressLine1 = "Street 1", city = "City", postalCode = "1000", countryCode = country)
}
