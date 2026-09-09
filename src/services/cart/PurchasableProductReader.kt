package services.cart

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import productdatabaseaccesslayer.ProductDataAccess
import services.common.ServiceErrorCode
import services.common.ServiceException
import support.optionalDecimalValue

class PurchasableProductReader(
    private val productDataAccess: ProductDataAccess,
    private val json: Json = Json
) {
    fun load(productId: Long): PurchasableProduct {
        val productJson = productDataAccess.getProductById(productId)
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.ProductDoesNotExist,
                description = "Product $productId does not exist"
            )
        val product = json.parseToJsonElement(productJson).jsonObject
        val order = product.purchaseOrder(productId)

        return PurchasableProduct(
            m2PerBox = order.decimalValue(productId, "m2_per_box"),
            pricePerM2 = order.decimalValue(productId, "client_price_per_m2")
        )
    }

    private fun JsonObject.purchaseOrder(productId: Long): JsonObject {
        val purchaseInformation = this["purchase_information"] as? JsonObject
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.ProductPurchaseInformationMissing,
                description = "Product $productId purchase information is missing"
            )

        return purchaseInformation["order"] as? JsonObject
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.ProductOrderInformationMissing,
                description = "Product $productId order information is missing"
            )
    }

    private fun JsonObject.decimalValue(productId: Long, name: String): BigDecimal {
        val errorCode = when (name) {
            "m2_per_box" -> ServiceErrorCode.ProductM2PerBoxMissing
            "client_price_per_m2" -> ServiceErrorCode.ProductClientPricePerM2Missing
            else -> ServiceErrorCode.ProductPurchaseInformationMissing
        }

        return optionalDecimalValue(name)
            ?: throw ServiceException(
                errorCode = errorCode,
                description = "Product $productId ${errorCode.defaultMessage.replaceFirstChar { it.lowercase() }}"
            )
    }
}

data class PurchasableProduct(
    val m2PerBox: BigDecimal,
    val pricePerM2: BigDecimal
)

