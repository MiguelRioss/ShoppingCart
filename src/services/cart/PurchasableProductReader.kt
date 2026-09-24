package services.cart

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess
import services.common.ServiceErrorCode
import services.common.ServiceException
import support.optionalDecimalValue

/** Reads and validates purchasable pricing metadata from catalogue JSON. */
class PurchasableProductReader(
    private val productDataAccess: ProductDataAccess,
    private val json: Json = Json
) {

    /** Loads normal-product terms and optional sample purchase terms. */
    fun load(
        productId: Long
    ): PurchasableProduct {

        val productJson =
            productDataAccess.getProductById(
                productId
            )
                ?: throw ServiceException(
                    errorCode =
                        ServiceErrorCode.ProductDoesNotExist,
                    description =
                        "Product $productId does not exist"
                )

        val product =
            json.parseToJsonElement(
                productJson
            ).jsonObject

        val purchaseInformation =
            product["purchase_information"] as? JsonObject

        val sample =
            (purchaseInformation?.get("sample") as? JsonObject)?.let { sampleJson ->
                PurchasableSample(
                    available =
                        sampleJson["sample_available"]
                            ?.jsonPrimitive
                            ?.booleanOrNull
                            ?: false,

                    price =
                        sampleJson.optionalDecimalValue("sample_price"),

                    maxQuantity =
                        sampleJson["sample_max_quantity"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.toIntOrNull()
                )
            }

        val order =
            product.purchaseOrder(
                productId
            )

        return PurchasableProduct(
            name =
                product["title"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Product $productId",

            imageUrl =
                product["image"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf {
                        it.isNotBlank()
                    },

            m2PerBox =
                order.decimalValue(
                    productId,
                    "m2_per_box"
                ),

            pricePerM2 =
                order.decimalValue(
                    productId,
                    "client_price_per_m2"
                ),
            sample = sample,
        )
    }

    private fun JsonObject.purchaseOrder(
        productId: Long
    ): JsonObject {

        val purchaseInformation =
            this["purchase_information"] as? JsonObject
                ?: throw ServiceException(
                    errorCode =
                        ServiceErrorCode.ProductPurchaseInformationMissing,
                    description =
                        "Product $productId purchase information is missing"
                )

        return purchaseInformation["order"] as? JsonObject
            ?: throw ServiceException(
                errorCode =
                    ServiceErrorCode.ProductOrderInformationMissing,
                description =
                    "Product $productId order information is missing"
            )
    }

    private fun JsonObject.decimalValue(
        productId: Long,
        name: String
    ): BigDecimal {

        val errorCode =
            when (name) {
                "m2_per_box" ->
                    ServiceErrorCode.ProductM2PerBoxMissing

                "client_price_per_m2" ->
                    ServiceErrorCode.ProductClientPricePerM2Missing

                else ->
                    ServiceErrorCode.ProductPurchaseInformationMissing
            }

        return optionalDecimalValue(
            name
        )
            ?: throw ServiceException(
                errorCode = errorCode,
                description =
                    "Product $productId " +
                            errorCode.defaultMessage
                                .replaceFirstChar {
                                    it.lowercase()
                                }
            )
    }
}

/** Catalogue information required to price cart and checkout lines. */
data class PurchasableProduct(
    val name: String,
    val imageUrl: String?,
    val m2PerBox: BigDecimal,
    val pricePerM2: BigDecimal,
    val sample: PurchasableSample?
)
/** Availability, price, and per-cart limit for product samples. */
data class PurchasableSample(
    val available: Boolean,
    val price: BigDecimal?,
    val maxQuantity: Int?
)
