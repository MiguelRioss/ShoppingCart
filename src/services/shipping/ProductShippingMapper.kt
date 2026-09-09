package services.shipping

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import domain.shipping.ShippingQuotation
import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import support.objectValueOrNull
import support.optionalDecimalValue
import support.stringValue

class ProductShippingMapper {

    fun packageSizeFor(
        product: JsonObject,
        quotation: ShippingQuotation
    ): ShippingPackageSize {
        val packingSizes = product
            .purchaseInformation()
            .objectValueOrNull("shipping")
            ?.get("packing_size") as? JsonArray
            ?: error("Product ${quotation.productId} shipping packing_size is missing")

        val packingSize = packingSizes
            .mapNotNull { it as? JsonObject }
            .firstOrNull {
                it.optionalDecimalValue("quantity")
                    ?.compareTo(quotation.quantityM2) == 0
            }
            ?: error("No packing configuration for requested quantity")

        return ShippingPackageSize(
            quantity = packingSize.requiredDecimal("quantity"),
            length = packingSize.requiredDecimal("length"),
            width = packingSize.requiredDecimal("width"),
            height = packingSize.requiredDecimal("height"),
            packedWeight = packingSize.requiredDecimal("packed_weight"),
            packagingCost = packingSize.optionalDecimalValue("packaging_cost"),
            packaging = packingSize.stringValue("packaging")
        )
    }

    fun collectionAddress(product: JsonObject): ShippingAddress {
        val supplier = product.objectValueOrNull("supplier")
            ?: error("Product supplier is missing")

        return ShippingAddress(
            streetLines = listOfNotNull(
                supplier.stringValue("address"),
                supplier.stringValue("address_2")
            ),
            city = supplier.stringValue("city_collection")
                ?: supplier.stringValue("city")
                ?: error("Supplier city is missing"),
            postalCode = supplier.stringValue("post_code_collection")
                ?: supplier.stringValue("post_code")
                ?: error("Supplier post code is missing"),
            countryCode = supplier.stringValue("country_collection")
                ?: supplier.stringValue("country")
                ?: error("Supplier country is missing")
        )
    }

    private fun JsonObject.purchaseInformation(): JsonObject =
        objectValueOrNull("purchase_information")
            ?: error("Product purchase_information is missing")

    private fun JsonObject.requiredDecimal(name: String): BigDecimal =
        optionalDecimalValue(name)
            ?: error("$name is missing")
}