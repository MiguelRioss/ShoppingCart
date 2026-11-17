package services.order

import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import services.pdf.*
import java.time.LocalDate
import java.time.ZoneId
import java.math.BigDecimal

internal class OrderInvoicePreview(private val products: ProductDataAccess) {
    fun build(orderId: String, snapshot: JsonObject, invoiceNumber: String, seller: DocumentParty, estimate: String): DeFerrantiOrderDocuments {
        val address = snapshot.objectValue("deliveryAddress")
        val customer = DocumentParty(
            name = snapshot.text("customerFirstName") ?: address.text("company") ?: missing("customerFirstName"),
            addressLines = listOfNotNull(
                address.text("addressLine1") ?: missing("deliveryAddress.addressLine1"),
                address.text("addressLine2"),
                listOfNotNull(address.text("postcode"), address.text("townOrCity")).joinToString(" ").takeIf(String::isNotBlank),
                address.text("country") ?: missing("deliveryAddress.country")
            ),
            email = snapshot.text("customerEmail") ?: missing("customerEmail")
        )
        val items = (snapshot["items"] as? JsonArray).orEmpty().map { it.jsonObject }.filter { it.text("productId") != "0" }
        require(items.isNotEmpty()) { "Order has no products" }
        val cartProducts = (snapshot["cartProducts"] as? JsonArray).orEmpty().map { it.jsonObject }
        val shipping = snapshot["shipping"] as? JsonObject
        val details = items.map { item ->
            val id = item.text("productId")?.toLongOrNull() ?: missing("items.productId")
            Json.parseToJsonElement(products.getProductDetailsById(id) ?: missing("catalogue.product.$id")).jsonObject
        }
        val suppliers = details.map { it.objectValue("supplier") }
        require(suppliers.map { it.text("id") }.distinct().size == 1) { "Invoice preview requires products from one supplier" }
        val supplier = suppliers.first()
        val supplierName = supplier.text("name") ?: missing("supplier.name")
        val supplierParty = DocumentParty(
            name = supplierName,
            addressLines = listOfNotNull(
                supplier.text("address") ?: missing("supplier.address"),
                supplier.text("address_2")?.trim()?.takeIf(String::isNotBlank),
                listOfNotNull(supplier.text("post_code"), supplier.text("city")).joinToString(" ").takeIf(String::isNotBlank),
                supplier.text("country") ?: missing("supplier.country")
            ),
            contactName = supplier.text("contact_name"),
            phone = supplier.text("phone"),
            email = supplier.text("email")
        )
        val shipFrom = DocumentParty(
            name = supplierName,
            addressLines = listOfNotNull(
                supplier.text("address_2")?.trim() ?: missing("supplier.address_2"),
                listOfNotNull(supplier.text("post_code_collection"), supplier.text("city_collection")).joinToString(" ").takeIf(String::isNotBlank),
                supplier.text("country_collection") ?: missing("supplier.country_collection")
            )
        )
        val packings = items.mapIndexed { index, item ->
            val id = item.text("productId") ?: missing("items[$index].productId")
            val selection = cartProducts.firstOrNull { it.text("productId") == id }
                ?: missing("cartProducts.productId=$id")
            val area = selection.text("squareMeters")?.toBigDecimalOrNull()
                ?: missing("cartProducts[$id].squareMeters")
            val sizes = details[index].objectValue("purchase_information")
                .objectValue("shipping")["packing_size"] as? JsonArray
                ?: missing("catalogue.product.$id.shipping.packing_size")
            val packing = sizes.map { it.jsonObject }.firstOrNull {
                it.text("quantity")?.toBigDecimalOrNull()?.compareTo(area) == 0
            } ?: missing("catalogue.product.$id.packing_size for $area m2")
            for (field in listOf("length", "width", "height", "packed_weight")) {
                packing.text(field) ?: missing("catalogue.product.$id.packing_size.$field")
            }
            packing
        }
        val goods = items.mapIndexed { index, item ->
            val product = details[index]
            val purchase = product.objectValue("purchase_information")
            val customs = purchase.objectValue("tax_freight_and_customs")
            val selection = cartProducts.firstOrNull { it.text("productId") == item.text("productId") }
            val packing = packings[index]
            val order = purchase.objectValue("order")
            val area = selection?.text("squareMeters")?.toBigDecimalOrNull() ?: missing("cartProducts.squareMeters")
            val boxes = selection.text("amountBoxes")?.toIntOrNull() ?: missing("cartProducts.amountBoxes")
            val units = order.text("units_per_box")?.toIntOrNull() ?: missing("catalogue.product.${item.text("productId")}.units_per_box")
            val netWeightPerTile = order.text("net_weight_per_tile_item")
                ?.toBigDecimalOrNull()
                ?.takeIf { it > BigDecimal.ZERO }
            val netWeight = netWeightPerTile?.multiply(BigDecimal(boxes.toLong() * units.toLong()))
            val lineTotal = item.text("amountTotal")?.toBigDecimalOrNull() ?: missing("items[$index].amountTotal")
            DocumentGoodsLine(
                productName = item.text("name") ?: missing("items[$index].name"),
                specificationLines = listOfNotNull(customs.text("customs_description")),
                area = "$area m2",
                boxes = "$boxes boxes",
                tiles = "${boxes * units} tiles",
                unitPrice = "${snapshot.text("currency")?.uppercase() ?: missing("currency")} ${lineTotal.divide(area, 2, java.math.RoundingMode.HALF_UP)}",
                lineTotal = "${snapshot.text("currency")?.uppercase() ?: missing("currency")} $lineTotal",
                deFerrantiSku = order.text("product_sku"),
                supplierProductId = product.objectValue("supplier").text("supplier_product_id"),
                hsCode = customs.text("hs_commodity_code") ?: missing("catalogue.product.${item.text("productId")}.hs_commodity_code"),
                originCountry = customs.text("country_of_origin") ?: missing("catalogue.product.${item.text("productId")}.country_of_origin"),
                netWeight = netWeight?.stripTrailingZeros()?.toPlainString()?.let { "$it kg" },
                grossWeight = "${packing.text("packed_weight")} kg",
                packageDescription = packing.text("packaging") ?: "Package"
            )
        }
        val currency = snapshot.text("currency")?.uppercase() ?: missing("currency")
        val grossWeight = packings.sumOf { it.text("packed_weight")!!.toBigDecimal() }
        val packingCost = packings.mapNotNull { it.text("packaging_cost")?.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
        val packageDescriptions = packings.map { packing ->
            "${packing.text("length")} x ${packing.text("width")} x ${packing.text("height")} cm"
        }
        val shippingTotal = snapshot.text("shippingTotal") ?: missing("shippingTotal")
        val taxAmount = snapshot.text("taxAmount") ?: missing("taxAmount")
        val date = LocalDate.now(ZoneId.of(if (seller.name == "De Ferranti Limited") "Europe/London" else "Europe/Lisbon"))
        return DeFerrantiOrderDocuments(
            branding = DocumentBranding(
                resolvePdfLogoPath("src/main/resources/deferranti-logo.png"),
                footerText = seller.name
            ),
            references = DocumentReferences(
                purchaseOrderNumber = "PO-$orderId",
                purchaseOrderDate = date.toString(),
                customerOrderNumber = orderId,
                requiredDespatchDate = estimate,
                packingListNumber = "PL-$orderId",
                packingDate = date.toString(),
                deliveryNoteNumber = "DN-$orderId",
                deliveryDate = date.toString(),
                invoiceNumber = invoiceNumber
            ),
            parties = DocumentParties(customer, supplierParty, shipFrom, customer, seller, seller),
            goods = goods,
            totals = DocumentTotals("$currency ${snapshot.text("productSubtotal") ?: missing("productSubtotal")}", "$currency $taxAmount" + if (snapshot.text("taxCalculation") == "temporary_zero") " (temporary)" else "", "$currency ${snapshot.text("total") ?: missing("total")}", currency),
            packing = PackingDetails(packings.map { it.text("packaging") ?: "Package" }.distinct().joinToString(", "), "${packings.size} estimated", packageDescriptions.joinToString("; "), "$grossWeight kg estimated", goods.joinToString("; ") { it.boxes }, "$currency $packingCost estimated"),
            shipment = ShipmentDetails(shipping?.text("serviceName")?.substringBefore(' ') ?: "Courier pending", shipping?.text("serviceName") ?: shipping?.text("serviceType") ?: "Pending", "Pending", "Pending", "Sale of goods", "DDP", "DDP", "$currency $shippingTotal", "PAID - CHECKOUT PAYMENT CONFIRMED"),
            checks = DocumentChecks(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            notes = DocumentNotes(emptyList(), emptyList(), emptyList(), emptyList(), "", "", "")
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    private fun JsonObject.objectValue(key: String): JsonObject = this[key] as? JsonObject ?: missing(key)
    private fun missing(field: String): Nothing = throw IllegalArgumentException("Missing invoice source: $field")
}
