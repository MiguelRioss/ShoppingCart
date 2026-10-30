package services.orderemail

import kotlinx.serialization.json.*
import kotlin.test.*

class OrderDocumentDataParserTest {
    private fun input() = buildJsonObject {
        put("references", buildJsonObject { put("customerOrderNumber", "order-1"); put("invoiceNumber", "INV-1") })
        put("parties", buildJsonObject {
            listOf("purchaser", "supplier", "shipFrom", "deliverTo", "importerOfRecord", "seller").forEach { key ->
                put(key, buildJsonObject { put("name", key); put("addressLines", buildJsonArray { add("Actual address") }) })
            }
        })
        put("goods", buildJsonArray { add(buildJsonObject { put("productName", "Saved product"); put("lineTotal", "EUR 90.00") }) })
        put("totals", buildJsonObject { put("supplierSubtotal", "EUR 90.00"); put("vatTreatment", "Applicable VAT"); put("purchaseOrderTotal", "EUR 90.00") })
        put("packing", buildJsonObject {})
        put("shipment", buildJsonObject {})
        put("branding", buildJsonObject { put("logoPath", "caller-controlled-path") })
    }

    @Test fun `parses fulfilment data without accepting caller filesystem paths`() {
        val data = OrderDocumentDataParser.parse(input())
        assertEquals("order-1", data.references.customerOrderNumber)
        assertEquals("INV-1", data.references.invoiceNumber)
        assertEquals("Saved product", data.goods.single().productName)
        assertEquals("EUR 90.00", data.goods.single().lineTotal)
        assertFalse(data.branding.toString().contains("caller-controlled-path"))
    }

    @Test fun `rejects missing goods and party addresses`() {
        assertFailsWith<EmailPayloadValidationException> {
            OrderDocumentDataParser.parse(JsonObject(input() + ("goods" to JsonArray(emptyList()))))
        }
        val parties = input().getValue("parties").jsonObject
        assertFailsWith<EmailPayloadValidationException> {
            OrderDocumentDataParser.parse(JsonObject(input() + ("parties" to JsonObject(parties + ("seller" to buildJsonObject { put("name", "Seller") })))))
        }
    }
}
