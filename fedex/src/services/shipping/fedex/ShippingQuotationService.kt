package services.shipping

import domain.shipping.ShippingQuotation

interface ShippingQuotationService {

    fun quote(quotation: ShippingQuotation): ShippingQuotation
}
