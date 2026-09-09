package services.shipping.fedex.ratequotation

import java.math.BigDecimal

data class FedExCustomsDetail(
    val commodityDescription: String,
    val countryOfManufacture: String,
    val harmonizedCode: String,
    val quantity: Int,
    val quantityUnits: String,
    val unitPrice: BigDecimal,
    val customsValue: BigDecimal,
    val currency: String
)