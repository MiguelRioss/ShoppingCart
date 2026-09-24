package services.pdf

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess

/**
 * Builds test document data from an actual product-catalogue record.
 *
 * Product details and purchasing measurements come from [productDataAccess], while order,
 * customer, shipment, and document references remain test fixtures. The result must therefore
 * be used for integration checks and PDF previews only, never as a live order.
 */
class RealProductTestOrderDocuments(
    private val productDataAccess: ProductDataAccess
) {

    /**
     * Loads [productId], calculates display quantities for [quantityM2], and creates test data.
     *
     * @throws IllegalArgumentException when the product or required catalogue sections are absent
     */
    fun create(
        productId: Long,
        quantityM2: Double = 9.5,
        logoPath: String = "src/main/resources/deferranti-logo.png"
    ): DeFerrantiOrderDocuments {

        // =========================================================
        // LOAD REAL PRODUCT FROM PRODUCT DATABASE
        // =========================================================

        val productJson =
            requireNotNull(
                productDataAccess.getProductDetailsById(productId)
            ) {
                "Product $productId was not found"
            }

        val product =
            Json
                .parseToJsonElement(productJson)
                .jsonObject

        val title =
            requireNotNull(product.string("title")) {
                "Product $productId has no title"
            }

        // =========================================================
        // PRODUCT DATA SECTIONS
        // =========================================================

        val supplier =
            requireNotNull(
                product["supplier"]?.jsonObject
            ) {
                "Product $productId has no supplier information"
            }

        val purchaseInformation =
            requireNotNull(
                product["purchase_information"]?.jsonObject
            ) {
                "Product $productId has no purchase information"
            }

        val productInfo =
            requireNotNull(
                purchaseInformation["product_info"]?.jsonObject
            ) {
                "Product $productId has no product_info"
            }

        val identifiers =
            requireNotNull(
                purchaseInformation["product_identifiers"]?.jsonObject
            ) {
                "Product $productId has no product identifiers"
            }

        val order =
            requireNotNull(
                purchaseInformation["order"]?.jsonObject
            ) {
                "Product $productId has no order information"
            }

        val taxFreight =
            requireNotNull(
                purchaseInformation["tax_freight_and_customs"]?.jsonObject
            ) {
                "Product $productId has no tax/freight/customs information"
            }

        val shipping =
            requireNotNull(
                purchaseInformation["shipping"]?.jsonObject
            ) {
                "Product $productId has no shipping information"
            }

        // =========================================================
        // QUANTITY CALCULATIONS
        // =========================================================

        val requestedM2 =
            BigDecimal
                .valueOf(quantityM2)
                .stripTrailingZeros()

        val m2PerBox =
            requireNotNull(
                order.decimal("m2_per_box")
            ) {
                "Product $productId has no m2_per_box"
            }

        val unitsPerBox =
            requireNotNull(
                order.integer("units_per_box")
            ) {
                "Product $productId has no units_per_box"
            }

        val boxCalculation =
            requestedM2.divide(
                m2PerBox,
                10,
                RoundingMode.HALF_UP
            )

        require(
            boxCalculation.stripTrailingZeros().scale() <= 0
        ) {
            "$quantityM2 m2 is not a valid box quantity. " +
                    "Product $productId contains $m2PerBox m2 per box."
        }

        val boxes =
            boxCalculation.toInt()

        val tiles =
            boxes * unitsPerBox

        // =========================================================
        // REAL PRODUCT PRICE
        // =========================================================

        val clientPricePerM2 =
            requireNotNull(
                order.decimal("client_price_per_m2")
            ) {
                "Product $productId has no client_price_per_m2"
            }

        val clientPricePerBox =
            requireNotNull(
                order.decimal("client_price_per_box")
            ) {
                "Product $productId has no client_price_per_box"
            }

        val lineTotal =
            clientPricePerM2
                .multiply(requestedM2)

        val boxPriceTotal =
            clientPricePerBox
                .multiply(
                    BigDecimal.valueOf(
                        boxes.toLong()
                    )
                )

        check(
            lineTotal.compareTo(boxPriceTotal) == 0
        ) {
            "Product price mismatch. " +
                    "m2 price gives $lineTotal but " +
                    "box price gives $boxPriceTotal"
        }

        // =========================================================
        // REAL PACKING CONFIGURATION
        // =========================================================

        val packingSizes =
            requireNotNull(
                shipping["packing_size"]?.jsonArray
            ) {
                "Product $productId has no packing sizes"
            }

        val packing =
            packingSizes
                .map {
                    it.jsonObject
                }
                .firstOrNull { packingOption ->

                    packingOption
                        .decimal("quantity")
                        ?.compareTo(requestedM2) == 0
                }
                ?: error(
                    "No packing configuration exists for " +
                            "$quantityM2 m2 of product $productId"
                )

        val packingLength =
            requireNotNull(
                packing.string("length")
            ) {
                "Packing length missing"
            }

        val packingWidth =
            requireNotNull(
                packing.string("width")
            ) {
                "Packing width missing"
            }

        val packingHeight =
            requireNotNull(
                packing.string("height")
            ) {
                "Packing height missing"
            }

        val packedWeight =
            requireNotNull(
                packing.string("packed_weight")
            ) {
                "Packed weight missing"
            }

        val packagingCost =
            packing.decimal("packaging_cost")

        val packagingType =
            packing.string("packaging")
                ?: "Package"

        // =========================================================
        // PRODUCT IDENTIFIERS
        // =========================================================

        val sku =
            identifiers.string(
                "merchant_product_id"
            )
                ?: productInfo.string(
                    "product_id"
                )

        val supplierProductId =
            identifiers.string(
                "non_standardised_manufacturer_product_identifier"
            )

        val standardisedProductId =
            identifiers.string(
                "standardised_manufacturer_product_identifier"
            )

        // =========================================================
        // PRODUCT / CUSTOMS INFORMATION
        // =========================================================

        val hsCode =
            taxFreight.string(
                "hs_commodity_code"
            )

        val originCountry =
            taxFreight.string(
                "country_of_origin"
            )

        val customsDescription =
            taxFreight.string(
                "customs_description"
            )

        val incoterm =
            taxFreight.string(
                "default_incoterm"
            )
                ?: "DDP"

        val productLength =
            order.string(
                "product_length"
            )

        val productWidth =
            order.string(
                "product_width"
            )

        val productThickness =
            order.string(
                "product_thickness"
            )

        // =========================================================
        // SUPPLIER
        // =========================================================

        val supplierName =
            supplier.string("name")
                ?: "Unknown Supplier"

        /*
         * Supplier collection / business address
         */
        val supplierAddress =
            listOfNotNull(

                supplier
                    .string("address_2")
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    },

                joinPostcodeAndCity(
                    postcode =
                        supplier.string(
                            "post_code_collection"
                        ),
                    city =
                        supplier.string(
                            "city_collection"
                        )
                ),

                supplier.string(
                    "country_collection"
                )
            )

        /*
         * Actual dispatch address
         */
        val shipFromAddress =
            listOfNotNull(

                supplier
                    .string("address")
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    },

                joinPostcodeAndCity(
                    postcode =
                        supplier.string(
                            "post_code"
                        ),
                    city =
                        supplier.string(
                            "city"
                        )
                ),

                supplier.string(
                    "country"
                )
            )

        // =========================================================
        // COURIER
        // =========================================================

        val courier =
            taxFreight["courier_service"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.contentOrNull
                ?: "FedEx"

        // =========================================================
        // CREATE SHARED DOCUMENT DATA
        // =========================================================

        return DeFerrantiOrderDocuments(

            // =====================================================
            // BRANDING
            // =====================================================

            branding =
                DocumentBranding(
                    logoPath = logoPath,
                    subtitle =
                        "REAL PRODUCT TEST - NOT VALID FOR LIVE USE"
                ),

            // =====================================================
            // REFERENCES
            // =====================================================

            references =
                DocumentReferences(

                    purchaseOrderNumber =
                        "PO-TEST-$productId",

                    purchaseOrderDate =
                        "17/09/2026",

                    customerOrderNumber =
                        "ORDER-TEST-$productId",

                    requiredDespatchDate =
                        "TEST DATE",

                    packingListNumber =
                        "PL-TEST-$productId",

                    packingDate =
                        "TEST DATE",

                    deliveryNoteNumber =
                        "DN-TEST-$productId",

                    deliveryDate =
                        "TEST DATE",

                    invoiceNumber =
                        "INV-TEST-$productId"
                ),

            // =====================================================
            // PARTIES
            // =====================================================

            parties =
                DocumentParties(

                    purchaser =
                        deFerranti(),

                    supplier =
                        DocumentParty(

                            name =
                                supplierName,

                            addressLines =
                                supplierAddress,

                            contactName =
                                supplier.string(
                                    "contact_name"
                                ),

                            phone =
                                supplier.string(
                                    "phone_collection"
                                ),

                            email =
                                supplier.string(
                                    "email_collection"
                                ),

                            eoriNumber =
                                "PT517666448"
                        ),

                    shipFrom =
                        DocumentParty(

                            name =
                                supplierName,

                            addressLines =
                                shipFromAddress,

                            contactName =
                                supplier.string(
                                    "contact_name"
                                ),

                            phone =
                                supplier.string(
                                    "phone"
                                ),

                            email =
                                supplier.string(
                                    "email"
                                ),

                            eoriNumber =
                                "PT517666448"
                        ),

                    /*
                     * Fake checkout customer for this PDF test.
                     *
                     * Later these values will come from the
                     * completed checkout.
                     */
                    deliverTo =
                        DocumentParty(

                            name =
                                "TEST CUSTOMER",

                            addressLines =
                                listOf(
                                    "10 Test Street",
                                    "London SW1A 1AA",
                                    "United Kingdom"
                                ),

                            contactName =
                                "Test Customer",

                            phone =
                                "+44 7700 900000",

                            email =
                                "test@example.com",

                            customerType =
                                "Individual"
                        ),

                    importerOfRecord =
                        deFerranti(),

                    seller =
                        deFerranti()
                ),

            // =====================================================
            // GOODS
            // =====================================================

            goods =
                listOf(

                    DocumentGoodsLine(

                        productName =
                            title,

                        specificationLines =
                            listOfNotNull(

                                customsDescription,

                                if (
                                    productLength != null &&
                                    productWidth != null &&
                                    productThickness != null
                                ) {
                                    "Size $productLength x " +
                                            "$productWidth x " +
                                            "$productThickness cm"
                                } else {
                                    null
                                },

                                order
                                    .string(
                                        "product_stock_status"
                                    )
                                    ?.let {
                                        "Stock status: $it"
                                    },

                                order
                                    .string(
                                        "product_lead_time"
                                    )
                                    ?.let {
                                        "Lead time: $it"
                                    }
                            ),

                        area =
                            "${formatNumber(requestedM2)} m2",

                        boxes =
                            "$boxes boxes",

                        tiles =
                            "$tiles tiles",

                        /*
                         * REAL PRODUCT DATABASE PRICE
                         */
                        unitPrice =
                            formatEuro(
                                clientPricePerM2
                            ),

                        lineTotal =
                            formatEuro(
                                lineTotal
                            ),

                        unitPriceLabel =
                            "per m2",

                        deFerrantiSku =
                            sku,

                        supplierProductId =
                            supplierProductId,

                        hsCode =
                            hsCode,

                        originCountry =
                            originCountry,

                        /*
                         * Separate net weight is not currently
                         * stored for this order quantity.
                         */
                        netWeight =
                            null,

                        grossWeight =
                            "$packedWeight kg",

                        packageDescription =
                            packagingType,

                        standardisedProductId =
                            standardisedProductId
                    )
                ),

            // =====================================================
            // TOTALS
            // =====================================================

            totals =
                DocumentTotals(

                    supplierSubtotal =
                        formatEuro(
                            lineTotal
                        ),

                    vatTreatment =
                        order.string(
                            "vat_rate"
                        )
                            ?: "Destination VAT",

                    purchaseOrderTotal =
                        formatEuro(
                            lineTotal
                        )
                ),

            // =====================================================
            // PACKING
            // =====================================================

            packing =
                PackingDetails(

                    packageType =
                        packagingType,

                    packageCount =
                        "1 of 1",

                    dimensions =
                        "$packingLength x " +
                                "$packingWidth x " +
                                "$packingHeight cm",

                    grossWeight =
                        "$packedWeight kg",

                    contents =
                        "$boxes boxes / " +
                                "$tiles tiles / " +
                                "${formatNumber(requestedM2)} m2",

                    packingCost =
                        packagingCost
                            ?.let {
                                formatEuro(it)
                            },

                    handlingLines =
                        listOf(
                            "Fragile handmade ceramic tiles",
                            "Keep shipment upright and dry",
                            "Do not stack unless the confirmed packing specification permits",
                            "Check packaging before courier collection"
                        )
                ),

            // =====================================================
            // SHIPMENT
            // =====================================================

            shipment =
                ShipmentDetails(

                    courier =
                        courier,

                    /*
                     * These will come from the actual FedEx
                     * shipment response later.
                     */
                    service =
                        "TEST - FEDEX SERVICE NOT BOOKED",

                    trackingNumber =
                        "TEST-TRACKING-$productId",

                    awb =
                        "TEST-AWB-$productId",

                    purpose =
                        "Commercial sale",

                    incoterm =
                        "$incoterm, Incoterms 2020",

                    deliveryTerms =
                        "$incoterm - Incoterms 2020"
                ),

            // =====================================================
            // CHECKS
            // =====================================================

            checks =
                DocumentChecks(

                    supplierConfirmationLines =
                        listOf(
                            "Quantity confirmed: [YES / EXCEPTION]",
                            "Packing confirmed: [YES / EXCEPTION]",
                            "Despatch date: __________________",
                            "Confirmed by: __________________",
                            "Date: __________________"
                        ),

                    authorisationLines =
                        listOf(
                            "Authorised by: __________________",
                            "Date: __________________",
                            "Notes: __________________________"
                        ),

                    packedByLines =
                        listOf(
                            "Name: __________________________",
                            "Signature: _____________________",
                            "Date and time: _________________"
                        ),

                    finalCheckLines =
                        listOf(
                            "$boxes boxes confirmed: [YES / NO]",
                            "$tiles tiles confirmed: [YES / NO]",
                            "Packing dimensions confirmed: [YES / NO]",
                            "Gross weight confirmed: [YES / NO]",
                            "$courier label attached: [YES / NO]"
                        ),

                    deliveryConditionLines =
                        listOf(
                            "Please inspect the external packaging before acceptance.",
                            "Shipment received intact: [YES / NO]",
                            "Visible damage: [YES / NO]",
                            "Number of packages received: ______",
                            "Photographs taken if damaged: [YES / NO]"
                        ),

                    deliveryExceptionLines =
                        listOf(
                            "________________________________________",
                            "________________________________________",
                            "________________________________________",
                            "If damaged or incomplete, record the exception with the courier driver."
                        ),

                    receivedByLines =
                        listOf(
                            "Name: __________________________",
                            "Company: _______________________",
                            "Signature: _____________________",
                            "Date: __________________________",
                            "Time: __________________________"
                        ),

                    courierReceiptLines =
                        listOf(
                            "Driver name / ID: ______________",
                            "$courier reference: _____________",
                            "Signature: _____________________",
                            "Date and time: _________________"
                        )
                ),

            // =====================================================
            // NOTES
            // =====================================================

            notes =
                DocumentNotes(

                    requiredDocumentLines =
                        listOf(
                            "Commercial invoice",
                            "Packing list",
                            "$courier label and AWB",
                            "Proof of despatch",
                            "Origin evidence where preferential treatment is claimed"
                        ),

                    b2bB2cLines =
                        listOf(
                            "One document format applies to both.",
                            "For B2B, VAT number is stored on the commercial invoice.",
                            "The packing list contains no prices.",
                            "De Ferranti remains importer of record under DDP."
                        ),

                    deliveryImportantLines =
                        listOf(
                            "This delivery note confirms receipt only.",
                            "It does not confirm concealed goods are free from damage.",
                            "Prices are intentionally omitted.",
                            "De Ferranti Limited remains importer of record under DDP."
                        ),

                    documentMatchLines =
                        listOf(
                            "Order: ORDER-TEST-$productId",
                            "Commercial invoice: INV-TEST-$productId",
                            "Packing list: PL-TEST-$productId",
                            "Purchase order: PO-TEST-$productId",
                            "$courier tracking: TEST-TRACKING-$productId"
                        ),

                    purchaseOrderControlNote =
                        "TEST DOCUMENT. Generated using real product " +
                                "catalogue data for product $productId.",

                    packingListControlNote =
                        "TEST DOCUMENT. Packing list generated using " +
                                "the real product and packing configuration " +
                                "stored in the product database.",

                    deliveryNoteControlNote =
                        "TEST DOCUMENT. Product data is real. " +
                                "Customer, document references and courier " +
                                "booking values remain test data."
                )
        )
    }

    // =============================================================
    // DE FERRANTI PARTY
    // =============================================================

    private fun deFerranti(): DocumentParty =
        DocumentParty(

            name =
                "De Ferranti Limited",

            addressLines =
                listOf(
                    "South Park Studios, Suite 10",
                    "88 Peterborough Road",
                    "London SW6 3HH",
                    "United Kingdom"
                ),

            vatNumber =
                "GB947801596",

            eoriNumber =
                "TEST - VERIFIED EORI REQUIRED"
        )

    // =============================================================
    // JSON HELPERS
    // =============================================================

    private fun JsonObject.string(
        key: String
    ): String? =
        this[key]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }

    private fun JsonObject.decimal(
        key: String
    ): BigDecimal? =
        string(key)
            ?.toBigDecimalOrNull()

    private fun JsonObject.integer(
        key: String
    ): Int? =
        string(key)
            ?.toIntOrNull()

    // =============================================================
    // FORMATTING
    // =============================================================

    private fun formatEuro(
        amount: BigDecimal
    ): String =
        "EUR ${
            amount
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                )
                .toPlainString()
        }"

    private fun formatNumber(
        number: BigDecimal
    ): String =
        number
            .stripTrailingZeros()
            .toPlainString()

    private fun joinPostcodeAndCity(
        postcode: String?,
        city: String?
    ): String? {

        val result =
            listOfNotNull(

                postcode
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    },

                city
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
            )
                .joinToString(" ")

        return result
            .takeIf {
                it.isNotEmpty()
            }
    }
}
