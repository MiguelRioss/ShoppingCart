package services.shipping

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import domain.shipping.ShippingQuotation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import productdatabaseaccesslayer.ProductDataAccess

class ProductShippingReader(
    private val productDataAccess: ProductDataAccess,
    private val json: Json = Json,
    private val mapper: ProductShippingMapper = ProductShippingMapper()
) {
    fun packageSizeFor(quotation: ShippingQuotation): ShippingPackageSize {
        val product = productJson(quotation.productId)
        return mapper.packageSizeFor(product, quotation)
    }

    fun collectionAddressFor(productId: Long): ShippingAddress =
        mapper.collectionAddress(productJson(productId))

    private fun productJson(productId: Long): JsonObject {
        val product = productDataAccess.getProductDetailsById(productId)
            ?: error("Product $productId does not exist")

        return json.parseToJsonElement(product).jsonObject
    }
}
