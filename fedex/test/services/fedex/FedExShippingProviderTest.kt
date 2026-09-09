package services.shipping.fedex

import domain.shipping.Incoterm
import domain.shipping.ShippingAddress
import domain.shipping.ShippingOption
import domain.shipping.ShippingProviderType
import domain.shipping.ShippingQuotation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.shipping.fedex.ratequotation.FedExQuoteClient
import services.shipping.fedex.ratequotation.FedExQuoteRequest
import java.math.BigDecimal
import java.time.LocalDate

class FedExShippingProviderTest {

    private val productDataAccess = ProductCatalogDataSource()

    @Test
    fun `provider type is FEDEX`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        assertEquals(
            ShippingProviderType.FEDEX,
            provider.type
        )
    }

    @Test
    fun `quote sends catalog packing size and collection address to FedEx client`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("0.5"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = Incoterm.DAP,
            options = emptyList()
        )

        provider.quote(quotation)

        val request = requireNotNull(fedExQuoteClient.lastRequest)

        assertEquals(
            quotation.destinationAddress,
            request.destinationAddress
        )

        assertEquals(
            Incoterm.DAP,
            request.incoterm
        )

        assertEquals(
            BigDecimal("0.5"),
            request.packageSize.quantity
        )
    }

    @Test
    fun `quote uses product collection address as origin`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("0.5"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = null,
            options = emptyList()
        )

        provider.quote(quotation)

        val request = requireNotNull(fedExQuoteClient.lastRequest)

        assertTrue(request.originAddress.streetLines.isNotEmpty())
        assertTrue(request.originAddress.city.isNotBlank())
        assertTrue(request.originAddress.postalCode.isNotBlank())
        assertTrue(request.originAddress.countryCode.isNotBlank())
    }

    @Test
    fun `quote passes destination address unchanged`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val destinationAddress = ShippingAddress(
            streetLines = listOf("10 Downing Street"),
            city = "London",
            postalCode = "SW1A 2AA",
            countryCode = "GB"
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("1.0"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = destinationAddress,
            incoterm = Incoterm.DDP,
            options = emptyList()
        )

        provider.quote(quotation)

        val request = requireNotNull(fedExQuoteClient.lastRequest)

        assertEquals(
            destinationAddress,
            request.destinationAddress
        )
    }

    @Test
    fun `quote passes requested incoterm to FedEx client`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("1.0"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = Incoterm.DDP,
            options = emptyList()
        )

        provider.quote(quotation)

        val request = requireNotNull(fedExQuoteClient.lastRequest)

        assertEquals(
            Incoterm.DDP,
            request.incoterm
        )
    }

    @Test
    fun `quote allows null incoterm`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("1.0"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = null,
            options = emptyList()
        )

        provider.quote(quotation)

        val request = requireNotNull(fedExQuoteClient.lastRequest)

        assertEquals(
            null,
            request.incoterm
        )
    }

    @Test
    fun `quote returns shipping options supplied by FedEx client`() {
        val expectedOptions = listOf(
            testShippingOption()
        )

        val fedExQuoteClient = RecordingFedExQuoteClient(
            optionsToReturn = expectedOptions
        )

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("0.5"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = null,
            options = emptyList()
        )

        val result = provider.quote(quotation)

        assertEquals(
            expectedOptions,
            result.options
        )
    }

    @Test
    fun `quote preserves quotation data when adding shipping options`() {
        val expectedOptions = listOf(
            testShippingOption()
        )

        val fedExQuoteClient = RecordingFedExQuoteClient(
            optionsToReturn = expectedOptions
        )

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("1.0"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = Incoterm.DAP,
            options = emptyList()
        )

        val result = provider.quote(quotation)

        assertEquals(quotation.productId, result.productId)
        assertEquals(quotation.quantityM2, result.quantityM2)
        assertEquals(quotation.recipientAddress, result.recipientAddress)
        assertEquals(quotation.destinationAddress, result.destinationAddress)
        assertEquals(quotation.incoterm, result.incoterm)
        assertEquals(expectedOptions, result.options)
    }

    @Test
    fun `quote returns manual Portugal MC52 fallback when FedEx fails`() {
        val fedExQuoteClient = RecordingFedExQuoteClient(
            exceptionToThrow = IllegalStateException("FedEx unavailable")
        )

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient,
            manualFallbackQuote = ManualFedExFallbackQuote(
                priceProvider = FixedManualFedExFallbackPriceProvider(
                    price = BigDecimal("123.00")
                )
            )
        )

        val quotation = ShippingQuotation(
            productId = 9278L,
            quantityM2 = BigDecimal("0.5"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = ShippingAddress(
                streetLines = listOf("Avenida da Liberdade 100"),
                city = "Lisboa",
                postalCode = "1250-096",
                countryCode = "PT"
            ),
            incoterm = Incoterm.DAP,
            options = emptyList()
        )

        val result = provider.quote(quotation)

        assertEquals(1, result.options.size)
        assertEquals(
            "FEDEX_PRIORITY_MANUAL_FALLBACK",
            result.options.first().serviceCode
        )
        assertEquals(
            BigDecimal("123.00"),
            result.options.first().price
        )
        assertEquals(
            "EUR",
            result.options.first().currency
        )
    }
    @Test
    fun `quote fails when product has no packing configuration for requested quantity`() {
        val fedExQuoteClient = RecordingFedExQuoteClient()

        val provider = FedExShippingProvider(
            productDataAccess = productDataAccess,
            fedExQuoteClient = fedExQuoteClient
        )

        val quotation = ShippingQuotation(
            productId = 9093L,
            quantityM2 = BigDecimal("10.0"),
            recipientAddress = testRecipientAddress(),
            destinationAddress = testDestinationAddress(),
            incoterm = null,
            options = emptyList()
        )

        assertThrows(RuntimeException::class.java) {
            provider.quote(quotation)
        }

        assertEquals(
            null,
            fedExQuoteClient.lastRequest
        )
    }

    private fun testRecipientAddress() =
        ShippingAddress(
            streetLines = listOf("1 Client Street"),
            city = "London",
            postalCode = "SW1A 1AA",
            countryCode = "GB"
        )

    private fun testDestinationAddress() =
        ShippingAddress(
            streetLines = listOf("1 Delivery Street"),
            city = "London",
            postalCode = "SW1A 1AA",
            countryCode = "GB"
        )

    private fun testShippingOption(): ShippingOption =
        ShippingOption(
            shippingProviderType = ShippingProviderType.FEDEX,
            serviceCode = "FEDEX_INTERNATIONAL_PRIORITY",
            serviceName = "FedEx International Priority",
            price = BigDecimal("125.00"),
            currency = "EUR",
            estimatedDeliveryDate = LocalDate.of(2026, 9, 12),
            incoterm = Incoterm.DAP
        )

    private class RecordingFedExQuoteClient(
        private val optionsToReturn: List<ShippingOption> = emptyList(),
        private val exceptionToThrow: RuntimeException? = null
    ) : FedExQuoteClient {

        var lastRequest: FedExQuoteRequest? = null
            private set

        override fun quote(
            request: FedExQuoteRequest
        ): List<ShippingOption> {
            lastRequest = request
            exceptionToThrow?.let { throw it }
            return optionsToReturn
        }
    }

    private class FixedManualFedExFallbackPriceProvider(
        private val price: BigDecimal
    ) : ManualFedExFallbackPriceProvider {
        override fun mc52PortugalDomesticPriceEur(): BigDecimal = price
    }
}