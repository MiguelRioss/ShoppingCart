package services.shipping

import domain.cart.ShoppingCart
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.ceil
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

        val taxFreightAndCustoms =
            requireObject(
                purchaseInformation,
                "tax_freight_and_customs",
                "Product ${cartProduct.productId} has no customs information"
            )

        if (cartProduct.isSample) {
            return sampleShipmentRequest(
                cart = cart,
                product = product,
                supplier = supplier,
                purchaseInformation = purchaseInformation,
                taxFreightAndCustoms = taxFreightAndCustoms
            )
        }

        val squareMeters =
            requireNotNull(cartProduct.squareMeters)

        val amountBoxes =
            requireNotNull(cartProduct.amountBoxes)

        val shipping =
            requireObject(
                purchaseInformation,
                "shipping",
                "Product ${cartProduct.productId} has no shipping information"
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

    private fun sampleShipmentRequest(
        cart: ShoppingCart,
        product: JsonObject,
        supplier: JsonObject,
        purchaseInformation: JsonObject,
        taxFreightAndCustoms: JsonObject
    ): ShipmentShippingRequest {
        val cartProduct =
            cart.products.single()
        val sample =
            requireObject(
                purchaseInformation,
                "sample",
                "Product ${cartProduct.productId} has no sample information"
            )
        val sampleUnits =
            requireNotNull(cartProduct.sampleUnits)

        return ShipmentShippingRequest(
            from =
                ShipmentAddress(
                    postalCode = requireString(supplier, "post_code_collection"),
                    countryCode =
                        countryNameToIso2(
                            requireString(supplier, "country_collection")
                        )
                ),
            product =
                ShipmentPackage(
                    description =
                        product["title"]
                            ?.jsonPrimitive
                            ?.content
                            ?: requireString(taxFreightAndCustoms, "customs_description"),
                    countryOfManufacture =
                        countryNameToIso2(
                            requireString(taxFreightAndCustoms, "country_of_origin")
                        ),
                    quantity = sampleUnits,
                    quantityUnits = "PCS",
                    weight =
                        ShipmentWeight(
                            value =
                                requireString(sample, "sample_packed_weight")
                                    .toDouble() / 1000.0,
                            units = "KG"
                        ),
                    dimensions =
                        ShipmentDimensions(
                            length = sampleDimension(sample, "sample_length"),
                            width = sampleDimension(sample, "sample_width"),
                            height = sampleDimension(sample, "sample_thickness"),
                            units = "CM"
                        ),
                    customsValue = cartProduct.totalPricePerProduct.toDouble(),
                    customsCurrency = "EUR",
                    preferredCurrency = "EUR"
                )
        )
    }

    private fun sampleDimension(
        sample: JsonObject,
        name: String
    ): Int =
        ceil(
            requireString(sample, name).toDouble()
        ).toInt()

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
