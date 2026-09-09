package domain.shipping

interface ShippingProvider {

    val type: ShippingProviderType

    fun quote(quotation: ShippingQuotation): ShippingQuotation
}
