package services.orderemail

import db.UserRepository
import productdatabaseaccesslayer.ProductDataAccess
import kotlinx.serialization.json.*
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Prepares JSON for a later email API call. Never sends email or changes order state. */
class OrderEmailPayloadBuilder(private val users: UserRepository, private val catalogue: ProductDataAccess) {
    fun prepare(template: OrderEmailTemplate, order: JsonObject, context: JsonObject, generatedPdf: EmailPdfAttachment? = null): JsonObject {
        val snapshot = order["snapshot"] as? JsonObject ?: throw EmailPayloadValidationException(listOf("order.snapshot"))
        val status = order.text("status")
        if (template == OrderEmailTemplate.ORDER_CONFIRMATION && status != "PAID") throw EmailPayloadConflictException("Order confirmation requires a paid order")
        if (template.supplier && status != "PAID") throw EmailPayloadConflictException("Supplier emails require a paid order")
        if (template == OrderEmailTemplate.INCOMPLETE_CHECKOUT && status !in setOf("PENDING_PAYMENT", "EXPIRED")) throw EmailPayloadConflictException("Incomplete checkout email cannot be prepared for a paid or failed order")
        val fields = context["fields"] as? JsonObject ?: JsonObject(emptyMap())
        val allowed = template.requiredFields + when (template) {
            OrderEmailTemplate.REVISED_DESPATCH_DATE -> setOf("despatchUpdateNote")
            OrderEmailTemplate.POST_DELIVERY_THANK_YOU -> setOf("discount_terms")
            else -> emptySet()
        }
        val invalid = mutableListOf<String>()
        (context.keys - setOf("fields", "documents", "supplierId", "shipmentId")).forEach { invalid += "context.$it (unsupported)" }
        (fields.keys - allowed).forEach { invalid += "context.fields.$it (not overridable)" }
        if(context.containsKey("fields") && context["fields"] !is JsonObject) invalid += "context.fields (object required)"
        if(context.containsKey("documents") && context["documents"] !is JsonObject) invalid += "context.documents (object required)"
        if(!template.supplier && context.containsKey("supplierId")) invalid += "context.supplierId (not applicable)"
        val variables = fields.toMutableMap()
        template.requiredFields.forEach { if (fields.text(it).isNullOrBlank()) invalid += "context.fields.$it" }
        for (key in allowed) if (fields.containsKey(key) && (fields[key] !is JsonPrimitive || fields[key] is JsonNull)) invalid += "context.fields.$key"
        val dateKeys = setOf("expected_despatch_date", "required_despatch_date", "previous_despatch_date", "revised_despatch_date", "estimated_delivery_date", "delivery_date")
        val dates = mutableMapOf<String, LocalDate>()
        for (key in dateKeys.intersect(fields.keys)) {
            val date = runCatching { LocalDate.parse(fields.text(key)) }.getOrNull()
            if (date == null) invalid += "context.fields.$key (ISO date required)"
            else { dates[key] = date; variables[key] = JsonPrimitive(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))) }
        }
        if (template == OrderEmailTemplate.REVISED_DESPATCH_DATE && dates.size == 2 && dates.getValue("revised_despatch_date") <= dates.getValue("previous_despatch_date")) invalid += "revised_despatch_date (must be later than previous_despatch_date)"
        for (key in setOf("tracking_url", "marketing_consent_link").intersect(fields.keys)) if (!httpUrl(fields.text(key))) invalid += "context.fields.$key (HTTP(S) URL required)"

        val customer = order.text("userId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(users::getUser)
        val customerEmail = snapshot.text("customerEmail") ?: customer?.email
        val firstName = snapshot.text("customerFirstName") ?: customer?.firstName
        variables["order_reference"] = JsonPrimitive(order.text("id") ?: "")
        if (!template.supplier) {
            if (firstName.isNullOrBlank()) invalid += "customer_first_name"
            variables["customer_first_name"] = JsonPrimitive(firstName.orEmpty())
        }

        val savedItems = (snapshot["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.text("productId") != "0" }
        val cartItems = (snapshot["cartProducts"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val supplierId = context.text("supplierId")
        if (template.supplier && supplierId.isNullOrBlank()) invalid += "context.supplierId"
        val productCache = mutableMapOf<String, JsonObject?>()
        fun product(id: String): JsonObject? = productCache.getOrPut(id) {
            runCatching { catalogue.getProductDetailsById(id.toLong())?.let { Json.parseToJsonElement(it) as? JsonObject } }.getOrNull()
        }
        var supplier: JsonObject? = null
        var boxes = 0
        var area = java.math.BigDecimal.ZERO
        val products = buildJsonArray {
            if (template.productCards || template.supplier) savedItems.forEachIndexed { index, item ->
                val id = item.text("productId") ?: return@forEachIndexed
                val catalogueProduct = if (template.supplier || item.text("imageUrl").isNullOrBlank()) product(id) else null
                val itemSupplier = catalogueProduct?.get("supplier") as? JsonObject
                if (template.supplier) {
                    if (itemSupplier?.text("id") != supplierId) return@forEachIndexed
                    supplier = itemSupplier
                }
                val savedQuantity = cartItems.getOrNull(index)?.takeIf { it.text("productId") == id }
                if (template.productCards && savedQuantity == null) invalid += "products[$index].savedQuantityDetails"
                val sample = savedQuantity?.get("isSample")?.jsonPrimitive?.booleanOrNull == true
                val quantity = if (sample) savedQuantity?.text("sampleUnits") else savedQuantity?.text("squareMeters")
                val renderedQuantity = quantity ?: item.text("quantity")
                if(renderedQuantity?.toBigDecimalOrNull()?.signum() != 1) invalid += "products[$index].quantity"
                val unit = if (sample) "pieces" else if (quantity != null) "m²" else "units"
                boxes += savedQuantity?.text("amountBoxes")?.toIntOrNull() ?: 0
                area += savedQuantity?.text("squareMeters")?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                if (item.text("name").isNullOrBlank()) invalid += "products[$index].name"
                val image = item.text("imageUrl") ?: catalogueProduct?.text("image")
                if (template.productCards && !httpUrl(image)) invalid += "products[$index].imageUrl"
                val reference = item.text("supplierProductId") ?: itemSupplier?.text("supplier_product_id")
                if (template.supplier && template.productCards && reference.isNullOrBlank()) invalid += "products[$index].supplierProductId"
                add(buildJsonObject {
                    put("productId", id.toLong()); put("name", item.text("name").orEmpty())
                    put("quantity", quantity ?: item.text("quantity").orEmpty()); put("unit", unit)
                    if (image != null) put("imageUrl", image)
                    reference?.let { put("supplierProductId", it) }
                    // Supplier prices belong to the PO PDF; never substitute customer retail prices.
                    if (!template.supplier) {
                        val amount = item.text("amountTotal")
                        val currency = item.text("currency")
                        if (amount?.toBigDecimalOrNull() == null) invalid += "products[$index].lineTotal"
                        if (currency.isNullOrBlank() || !runCatching { java.util.Currency.getInstance(currency.uppercase()) }.isSuccess) invalid += "products[$index].currency"
                        put("lineTotal", amount.orEmpty()); put("currency", currency.orEmpty())
                    }
                })
            }
        }
        if ((template.productCards || template.supplier) && products.isEmpty()) invalid += "products (no matching saved order items)"
        if (template.productCards) variables["products"] = products
        if (template.supplier) {
            val contact = supplier?.text("contact_name")
            if (contact.isNullOrBlank()) invalid += "supplier_contact_name"
            variables["supplier_contact_name"] = JsonPrimitive(contact.orEmpty())
            if (template in setOf(OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER, OrderEmailTemplate.SUPPLIER_PACKING_LIST)) variables["boxes"] = JsonPrimitive(boxes)
            if (template == OrderEmailTemplate.SUPPLIER_PACKING_LIST) variables["quantity_m2"] = JsonPrimitive(area.toPlainString())
        }
        val recipientEmail = if (template.supplier) supplier?.text("email") else customerEmail
        if (recipientEmail.isNullOrBlank() || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(recipientEmail)) invalid += "recipient.email"
        val documents = context["documents"] as? JsonObject ?: JsonObject(emptyMap())
        val attachments = buildJsonArray {
            template.documentType?.let { type ->
                if (generatedPdf != null) {
                    if (generatedPdf.type != type) invalid += "attachment.type"
                    else add(generatedPdf.toJson())
                    return@let
                }
                val document = documents[type] as? JsonObject
                if (document == null || document.text("documentId").isNullOrBlank() || document.text("orderId") != order.text("id") || (template.supplier && document.text("supplierId") != supplierId) || document.text("filename")?.endsWith(".pdf", true) != true || !httpUrl(document.text("url"))) invalid += "context.documents.$type (matching orderId/supplierId, documentId, PDF filename and HTTP(S) URL required)"
                else add(buildJsonObject {
                    put("documentId", document.text("documentId")); put("filename", document.text("filename"))
                    put("url", document.text("url")); put("contentType", "application/pdf"); put("type", type)
                })
            }
        }
        if (invalid.isNotEmpty()) throw EmailPayloadValidationException(invalid.distinct())
        return buildJsonObject {
            put("schemaVersion", 1); put("brand", "deferranti"); put("templateId", template.id)
            put("orderId", order.text("id")); put("checkoutSessionId", order.text("checkoutSessionId"))
            put("to", buildJsonObject { put("email", recipientEmail); put("name", if (template.supplier) supplier?.text("contact_name") else firstName) })
            put("variables", JsonObject(variables)); put("attachments", attachments)
            put("idempotencyKey", listOfNotNull("order", order.text("id"), template.id, supplierId, context.text("shipmentId"), fields.text("revised_despatch_date"), generatedPdf?.documentId ?: template.documentType?.let { (documents[it] as? JsonObject)?.text("documentId") }).joinToString(":"))
        }
    }
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun httpUrl(value: String?): Boolean = value != null && runCatching { val url = URI(value); url.scheme in setOf("https", "http") && !url.host.isNullOrBlank() }.getOrDefault(false)
}
