package services.shipping

import domain.cart.ShoppingCart
import java.math.BigDecimal
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess
import shipment.core.ShipmentAddress
import shipment.core.ShipmentDimensions
import shipment.core.ShipmentPackage
import shipment.core.ShipmentWeight

class ProductShipmentRequestMapper(
    private val productDataAccess: ProductDataAccess
) {

    fun toShipmentShippingRequest(
        cart: ShoppingCart
    ): ShipmentShippingRequest {
        require(cart.products.size == 1) {
            "Shipping currently supports exactly one product per cart"
        }

        val cartProduct =
            cart.products.single()

        val product =
            Json.parseToJsonElement(
                requireNotNull(
                    productDataAccess.getProductDetailsById(
                        cartProduct.productId
                    )
                ) {
                    "Product ${cartProduct.productId} not found"
                }
            ).jsonObject
        val squareMeters =
            requireNotNull(cartProduct.squareMeters)

        val amountBoxes =
            requireNotNull(cartProduct.amountBoxes)

        val supplier =
            requireObject(
                product,
                "supplier",
                "Product ${cartProduct.productId} has no supplier"
            )

        val purchaseInformation =
            requireObject(
                product,
                "purchase_information",
                "Product ${cartProduct.productId} has no purchase information"
            )

        val shipping =
            requireObject(
                purchaseInformation,
                "shipping",
                "Product ${cartProduct.productId} has no shipping information"
            )

        val taxFreightAndCustoms =
            requireObject(
                purchaseInformation,
                "tax_freight_and_customs",
                "Product ${cartProduct.productId} has no customs information"
            )

        val packing =
            requirePackingForSquareMeters(
                shipping = shipping,
                requestedSquareMeters =
                    BigDecimal.valueOf(
                        cartProduct.squareMeters
                    ),
                productId = cartProduct.productId
            )

        return ShipmentShippingRequest(
            from =
                ShipmentAddress(
                    postalCode =
                        requireString(
                            supplier,
                            "post_code_collection"
                        ),
                    countryCode =
                        countryNameToIso2(
                            requireString(
                                supplier,
                                "country_collection"
                            )
                        )
                ),
            product =
                ShipmentPackage(
                    description =
                        requireString(
                            taxFreightAndCustoms,
                            "customs_description"
                        ),
                    countryOfManufacture =
                        countryNameToIso2(
                            requireString(
                                taxFreightAndCustoms,
                                "country_of_origin"
                            )
                        ),
                    quantity =
                        cartProduct.amountBoxes.coerceAtLeast(1),
                    quantityUnits = "PCS",
                    weight =
                        ShipmentWeight(
                            value =
                                requireString(
                                    packing,
                                    "packed_weight"
                                ).toDouble(),
                            units = "KG"
                        ),
                    dimensions =
                        ShipmentDimensions(
                            length =
                                requireString(
                                    packing,
                                    "length"
                                ).toInt(),
                            width =
                                requireString(
                                    packing,
                                    "width"
                                ).toInt(),
                            height =
                                requireString(
                                    packing,
                                    "height"
                                ).toInt(),
                            units = "CM"
                        ),
                    customsValue =
                        cartProduct.totalPricePerProduct.toDouble(),
                    customsCurrency = "EUR",
                    preferredCurrency = "EUR"
                )
        )
    }

    private fun requirePackingForSquareMeters(
        shipping: JsonObject,
        requestedSquareMeters: BigDecimal,
        productId: Long
    ): JsonObject =
        requireNotNull(
            shipping["packing_size"]
        ) {
            "Product $productId has no packing sizes"
        }
            .jsonArray
            .map {
                it.jsonObject
            }
            .firstOrNull { packingRow ->
                BigDecimal(
                    requireString(
                        packingRow,
                        "quantity"
                    )
                ).compareTo(
                    requestedSquareMeters
                ) == 0
            }
            ?: error(
                "No packing size found for $requestedSquareMeters m2 of product $productId"
            )

    private fun requireObject(
        jsonObject: JsonObject,
        key: String,
        errorMessage: String
    ): JsonObject =
        requireNotNull(
            jsonObject[key]
        ) {
            errorMessage
        }.jsonObject

    private fun requireString(
        jsonObject: JsonObject,
        key: String
    ): String =
        requireNotNull(
            jsonObject[key]
        ) {
            "Missing required product field: $key"
        }
            .jsonPrimitive
            .content

    private fun countryNameToIso2(
        countryName: String
    ): String {
        val countryCode =
            Locale.getISOCountries()
                .firstOrNull { code ->
                    Locale.Builder()
                        .setRegion(code)
                        .build()
                        .getDisplayCountry(Locale.ENGLISH)
                        .equals(countryName, ignoreCase = true)
                }

        return requireNotNull(countryCode) {
            "Unknown country: $countryName"
        }
    }
}
