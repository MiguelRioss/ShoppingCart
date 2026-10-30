package services.orderemail

import db.offline.InMemoryUserRepository
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import java.util.Base64
import kotlin.test.*

class PaymentConfirmationPayloadTest {
    private val catalogue = object : ProductDataAccess {
        override fun getAllProducts() = "[]"
        override fun getPurchasableProducts() = "[]"
        override fun getProductBySlug(productSlug: String) = "{}"
        override fun getProductById(productId: Long): String = error("Saved image must avoid catalogue lookup")
    }
    @Test fun `saved lead times and quantities generate real payment summary bytes`() {
        val order = Json.parseToJsonElement("""{
          "id":"order-1", "status":"PAID", "amountPaidMinor":11500,"paidCurrency":"eur",
          "snapshot":{
            "customerEmail":"actual@example.com","customerFirstName":"Miguel",
            "items":[
              {"productId":1,"name":"Saved tile","quantity":2,"amountTotal":"90.00","currency":"eur","imageUrl":"https://example.com/tile.png","generalLeadTime":"2-4 Weeks"},
              {"productId":0,"name":"Shipping","quantity":1,"amountTotal":"25.00","currency":"eur"}
            ],
            "cartProducts":[{"productId":1,"isSample":false,"squareMeters":1.5,"amountBoxes":2}]
          }
        }""").jsonObject
        val service = PaymentConfirmationPayload(OrderEmailPayloadBuilder(InMemoryUserRepository(), catalogue))
        val payload = service.prepare(order)
        val variables = payload.getValue("variables").jsonObject
        assertEquals("Saved tile: 2-4 Weeks", variables.getValue("lead_time_summary").jsonPrimitive.content)
        assertEquals("1.5", variables.getValue("products").jsonArray.single().jsonObject.getValue("quantity").jsonPrimitive.content)
        val attachment = payload.getValue("attachments").jsonArray.single().jsonObject
        assertEquals("payment-summary", attachment.getValue("type").jsonPrimitive.content)
        val bytes = Base64.getDecoder().decode(attachment.getValue("contentBase64").jsonPrimitive.content)
        assertTrue(bytes.size > 1000)
        assertEquals("%PDF-", bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII))
        val snapshot = order.getValue("snapshot").jsonObject
        val items = snapshot.getValue("items").jsonArray.toMutableList()
        items[0] = JsonObject(items[0].jsonObject - "generalLeadTime")
        val missing = service.prepare(JsonObject(order + ("snapshot" to JsonObject(snapshot + ("items" to JsonArray(items))))))
        assertTrue(missing.getValue("variables").jsonObject.getValue("lead_time_summary").jsonPrimitive.content.contains("To be confirmed"))
    }
}
