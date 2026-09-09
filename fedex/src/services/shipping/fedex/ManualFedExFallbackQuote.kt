package services.shipping.fedex

import domain.shipping.ShippingOption
import domain.shipping.ShippingProviderType
import domain.shipping.ShippingQuotation
import java.math.BigDecimal

class ManualFedExFallbackQuote(
    private val priceProvider: ManualFedExFallbackPriceProvider = EnvironmentManualFedExFallbackPriceProvider()
) {

    fun optionsFor(
        quotation: ShippingQuotation
    ): List<ShippingOption> {
        if (quotation.productId != MC52_PRODUCT_ID ||
            quotation.quantityM2.compareTo(MC52_SAMPLE_QUANTITY_M2) != 0 ||
            quotation.destinationAddress.countryCode.trim().uppercase() != "PT"
        ) {
            return emptyList()
        }

        val price =
            priceProvider.mc52PortugalDomesticPriceEur()
                ?: return emptyList()

        return listOf(
            ShippingOption(
                shippingProviderType = ShippingProviderType.FEDEX,
                serviceCode = "FEDEX_PRIORITY_MANUAL_FALLBACK",
                serviceName = "FedEx Priority - manual Portugal domestic fallback",
                price = price,
                currency = "EUR",
                estimatedDeliveryDate = null,
                incoterm = quotation.incoterm
            )
        )
    }

    private companion object {
        const val MC52_PRODUCT_ID = 9278L
        val MC52_SAMPLE_QUANTITY_M2 = BigDecimal("0.5")
    }
}

interface ManualFedExFallbackPriceProvider {
    fun mc52PortugalDomesticPriceEur(): BigDecimal?
}

class EnvironmentManualFedExFallbackPriceProvider : ManualFedExFallbackPriceProvider {
    override fun mc52PortugalDomesticPriceEur(): BigDecimal? =
        System.getenv("FEDEX_MANUAL_MC52_PT_DOMESTIC_PRICE_EUR")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toBigDecimalOrNull()
}