package services.orderemail

import db.offline.InMemoryUserRepository
import domain.user.User
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

class OrderEmailPayloadBuilderTest {
    private val users = InMemoryUserRepository()
    private val user = users.saveUser(User(UUID.randomUUID(), "actual@example.com", "not-public", LocalDateTime.now(), firstName = "Actual"))
    private val catalogue = object : ProductDataAccess {
        override fun getAllProducts() = "[]"
        override fun getPurchasableProducts() = "[]"
        override fun getProductBySlug(productSlug: String) = "{}"
        override fun getProductById(productId: Long) = getProductDetailsById(productId)
        override fun getProductDetailsById(productId: Long) = """{"title":"Changed catalogue title","image":"https://images.example.com/$productId.png","supplier":{"id":"${if(productId == 1L) "supplier-a" else "supplier-b"}","contact_name":"Supplier Contact","email":"supplier@example.com","supplier_product_id":"REAL-$productId"}}"""
    }
    private val builder = OrderEmailPayloadBuilder(users, catalogue)
    private fun order(status: String = "PAID") = buildJsonObject {
        put("id", "order-id"); put("userId", user.id.toString()); put("checkoutSessionId", "cs-order"); put("status", status)
        put("snapshot", buildJsonObject {
            put("customerEmail", "checkout@example.com"); put("customerFirstName", "Snapshot Name")
            put("items", Json.parseToJsonElement("""[{"productId":1,"name":"Exact saved backend title","quantity":2,"amountTotal":"90.00","currency":"eur"},{"productId":2,"name":"Exact saved sample title","quantity":3,"amountTotal":"30.00","currency":"eur"},{"productId":0,"name":"Shipping","quantity":1,"amountTotal":"10.00","currency":"eur"}]"""))
            put("cartProducts", Json.parseToJsonElement("""[{"productId":1,"isSample":false,"squareMeters":1.5,"amountBoxes":2},{"productId":2,"isSample":true,"sampleUnits":3}]"""))
        })
    }
    private fun context(template: OrderEmailTemplate): JsonObject = buildJsonObject {
        if(template.supplier) put("supplierId", "supplier-a")
        put("fields", buildJsonObject {
            template.requiredFields.forEach { key -> put(key, when(key) {
                "previous_despatch_date" -> "2026-11-12"
                "revised_despatch_date" -> "2026-11-16"
                "tracking_url", "marketing_consent_link" -> "https://example.com/action"
                else -> if(key.endsWith("_date")) "2026-11-19" else "Actual workflow value"
            }) }
        })
        template.documentType?.let { type -> put("documents", buildJsonObject {
            put(type, buildJsonObject {
                put("documentId", "stored-document-id"); put("orderId", "order-id")
                if(template.supplier) put("supplierId", "supplier-a")
                put("filename", "$type.pdf"); put("url", "https://files.example.com/existing.pdf")
            })
        }) }
    }
    @Test fun allNineTemplatesProduceCompletePayloads() {
        assertEquals(9, OrderEmailTemplate.entries.size)
        OrderEmailTemplate.entries.forEach { template ->
            val result = builder.prepare(template, order(if(template == OrderEmailTemplate.INCOMPLETE_CHECKOUT) "PENDING_PAYMENT" else "PAID"), context(template))
            assertEquals(template.id, result["templateId"]?.jsonPrimitive?.content)
            assertEquals("deferranti", result["brand"]?.jsonPrimitive?.content)
            assertFalse(result.toString().contains("{{"))
            assertFalse(result.toString().contains("not-public"))
            assertEquals(if(template.supplier) "supplier@example.com" else "checkout@example.com", result["to"]!!.jsonObject["email"]!!.jsonPrimitive.content)
            val variables = result["variables"]!!.jsonObject
            template.requiredFields.forEach { assertTrue(variables.containsKey(it)) }
            assertEquals(if(template.documentType == null) 0 else 1, result["attachments"]!!.jsonArray.size)
        }
    }
    @Test fun preservesSnapshotTitlesQuantitiesPricesAndSeparatesSamples() {
        val result = builder.prepare(OrderEmailTemplate.ORDER_CONFIRMATION, order(), context(OrderEmailTemplate.ORDER_CONFIRMATION))
        val items = result["variables"]!!.jsonObject["products"]!!.jsonArray
        assertEquals(2,items.size)
        assertEquals("Exact saved backend title",items[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("1.5",items[0].jsonObject["quantity"]!!.jsonPrimitive.content)
        assertEquals("m²",items[0].jsonObject["unit"]!!.jsonPrimitive.content)
        assertEquals("pieces",items[1].jsonObject["unit"]!!.jsonPrimitive.content)
        assertEquals("90.00",items[0].jsonObject["lineTotal"]!!.jsonPrimitive.content)
        assertEquals("Snapshot Name",result["variables"]!!.jsonObject["customer_first_name"]!!.jsonPrimitive.content)
    }
    @Test fun supplierSelectionDoesNotLeakOtherSuppliersOrRetailPrices() {
        val template = OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER
        val result = builder.prepare(template,order(),context(template))
        val items=result["variables"]!!.jsonObject["products"]!!.jsonArray
        assertEquals(1,items.size);assertFalse(items[0].jsonObject.containsKey("lineTotal"))
        assertEquals("REAL-1",items[0].jsonObject["supplierProductId"]!!.jsonPrimitive.content)
    }
    @Test fun rejectsMissingWorkflowDataAndProductOverrides() {
        assertFailsWith<EmailPayloadValidationException> { builder.prepare(OrderEmailTemplate.ORDER_CONFIRMATION,order(),JsonObject(emptyMap())) }
        val context = JsonObject(context(OrderEmailTemplate.ORDER_CONFIRMATION) + ("fields" to buildJsonObject { put("products", "fake");put("customer_first_name","fake") }))
        val error=assertFailsWith<EmailPayloadValidationException> { builder.prepare(OrderEmailTemplate.ORDER_CONFIRMATION,order(),context) }
        assertTrue(error.fields.any{it.contains("not overridable")})
    }
    @Test fun rejectsWrongPaymentStateAndNonDelayedDates() {
        assertFailsWith<EmailPayloadConflictException> { builder.prepare(OrderEmailTemplate.INCOMPLETE_CHECKOUT,order(),context(OrderEmailTemplate.INCOMPLETE_CHECKOUT)) }
        assertFailsWith<EmailPayloadConflictException> { builder.prepare(OrderEmailTemplate.ORDER_CONFIRMATION,order("PENDING_PAYMENT"),context(OrderEmailTemplate.ORDER_CONFIRMATION)) }
        assertFailsWith<EmailPayloadValidationException> { builder.prepare(OrderEmailTemplate.REVISED_DESPATCH_DATE,order(),buildJsonObject {put("fields",buildJsonObject {put("previous_despatch_date","2026-11-12");put("revised_despatch_date","2026-11-12")})}) }
    }
    @Test fun rejectsDocumentFromAnotherOrderAndUnsafeActionLinks() {
        val template=OrderEmailTemplate.ORDER_CONFIRMATION
        val original=context(template)
        val wrongDocument=buildJsonObject {
            put("fields",original.getValue("fields"))
            put("documents",buildJsonObject { put("invoice",buildJsonObject {
                put("orderId","another-order");put("documentId","stored-doc");put("filename","invoice.pdf");put("url","https://example.com/invoice.pdf")
            }) })
        }
        assertFailsWith<EmailPayloadValidationException>{builder.prepare(template,order(),wrongDocument)}
        val tracking=context(OrderEmailTemplate.GOODS_DESPATCHED)
        val unsafe=JsonObject(tracking + ("fields" to JsonObject(tracking.getValue("fields").jsonObject + ("tracking_url" to JsonPrimitive("javascript:alert(1)")))))
        assertFailsWith<EmailPayloadValidationException>{builder.prepare(OrderEmailTemplate.GOODS_DESPATCHED,order(),unsafe)}
    }
    @Test fun differentSupplierCannotReceiveAnUnrelatedProductsDocument() {
        val template=OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER
        val selected=JsonObject(context(template) + ("supplierId" to JsonPrimitive("not-in-order")))
        val error=assertFailsWith<EmailPayloadValidationException>{builder.prepare(template,order(),selected)}
        assertTrue(error.fields.any{it.contains("no matching saved order items")})
    }
}
