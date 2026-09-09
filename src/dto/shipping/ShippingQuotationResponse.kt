package dto.shipping

import domain.shipping.Incoterm
import domain.shipping.ShippingProviderType
import domain.shipping.ShippingQuotation
import java.math.BigDecimal
import java.time.LocalDate

data class ShippingQuotationResponse(
    val options: List<ShippingOptionResponse>
)

data class ShippingOptionResponse(
    val shippingProviderType: ShippingProviderType,
    val serviceCode: String,
    val serviceName: String,
    val price: BigDecimal,
    val currency: String,
    val estimatedDeliveryDate: LocalDate? = null,
    val incoterm: Incoterm? = null
)

fun ShippingQuotation.toResponse(): ShippingQuotationResponse =
    ShippingQuotationResponse(
        options = options.map { option ->
            ShippingOptionResponse(
                shippingProviderType = option.shippingProviderType,
                serviceCode = option.serviceCode,
                serviceName = option.serviceName,
                price = option.price,
                currency = option.currency,
                estimatedDeliveryDate = option.estimatedDeliveryDate,
                incoterm = option.incoterm
            )
        }
    )
