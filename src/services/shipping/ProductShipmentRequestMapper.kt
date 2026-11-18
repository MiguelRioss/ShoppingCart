package services.shipping

import ShoppingCartProduct
import domain.cart.ShoppingCart
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.ceil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentContact
import domain.shipment.ShipmentDimensions
import domain.shipment.ShipmentPackage
import domain.shipment.ShipmentWeight

/**
 * Converts catalogue shipping metadata and cart quantities into physical packages.
 * Cart lines with the same supplier address are grouped into one carrier request;
 * different supplier addresses produce independent requests.
 */
class ProductShipmentRequestMapper(
    private val productDataAccess: ProductDataAccess,
    private val validateGrossVsCalculatedNet: Boolean = false
) {

    /**
     * Builds one carrier-neutral shipment request per supplier origin.
     *
     * Products sharing an origin travel in the same FedEx multi-package shipment.
     * Products from different origins cannot share a carrier shipment and are
     * therefore returned as separate requests.
     *
     * @throws IllegalArgumentException when the cart is empty
     */
    fun toShipmentShippingRequests(
        cart: ShoppingCart
    ): List<ShipmentShippingRequest> {
        require(cart.products.isNotEmpty()) {
            "Shipping requires at least one cart product"
        }

        val productRequests =
            cart.products.map {
                toSingleProductShipmentRequest(it)
            }

        return productRequests
            .groupBy(ShipmentShippingRequest::from)
            .map { (origin, requests) ->
                require(requests.map(ShipmentShippingRequest::shipperContact).distinct().size == 1) {
                    "Products shipping from $origin have conflicting supplier contacts"
                }
                ShipmentShippingRequest(
                    from = origin,
                    shipperContact = requests.first().shipperContact,
                    products = requests.flatMap(ShipmentShippingRequest::products)
                )
            }
    }

    private fun toSingleProductShipmentRequest(
        cartProduct: ShoppingCartProduct
    ): ShipmentShippingRequest {

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
        val supplierContact = supplierContact(supplier)

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
                cartProduct = cartProduct,
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

        if (validateGrossVsCalculatedNet) validateCatalogWeights(
            product, packing,
            amountBoxes * (purchaseInformation["order"]?.jsonObject?.get("units_per_box")
                ?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0),
            cartProduct.productId
        )

        return ShipmentShippingRequest(
            from =
                ShipmentAddress(
                    addressLine1 = requireString(supplier, "address_2"),
                    city = requireString(supplier, "city_collection"),
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
            shipperContact = supplierContact,
            products =
                listOf(
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
                    customsCurrency = "EUR"
                    )
                )
        )
    }

    private fun sampleShipmentRequest(
        cartProduct: ShoppingCartProduct,
        product: JsonObject,
        supplier: JsonObject,
        purchaseInformation: JsonObject,
        taxFreightAndCustoms: JsonObject
    ): ShipmentShippingRequest {
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
                    addressLine1 = requireString(supplier, "address_2"),
                    city = requireString(supplier, "city_collection"),
                    postalCode = requireString(supplier, "post_code_collection"),
                    countryCode =
                        countryNameToIso2(
                            requireString(supplier, "country_collection")
                        )
                ),
            shipperContact = supplierContact(supplier),
            products =
                listOf(
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
                    customsCurrency = "EUR"
                    )
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

    private fun validateCatalogWeights(
        product: JsonObject,
        packing: JsonObject,
        tileCount: Int,
        productId: Long
    ) {
        val purchase = product["purchase_information"]?.jsonObject ?: return
        val order = purchase["order"]?.jsonObject ?: return
        val netWeightPerTile = order["net_weight_per_tile_item"]
            ?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
            ?: return
        val grossWeight = packing["packed_weight"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
            ?: return
        val netWeight = netWeightPerTile.multiply(BigDecimal.valueOf(tileCount.toLong()))
        require(grossWeight >= netWeight) {
            "Product $productId has gross packed weight $grossWeight kg below calculated net tile weight $netWeight kg"
        }
    }

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

    private fun supplierContact(supplier: JsonObject): ShipmentContact? {
        val phone = supplier["phone"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        return ShipmentContact(
            personName = supplier["contact_name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                ?: requireString(supplier, "name"),
            phoneNumber = phone,
            companyName = supplier["name"]?.jsonPrimitive?.contentOrNull
        )
    }

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
