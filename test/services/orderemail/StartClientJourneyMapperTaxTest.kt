package services.orderemail

import db.offline.InMemoryOrderRepository
import kotlinx.serialization.json.*
import services.order.OrderManager
import productdatabaseaccesslayer.ProductDataAccess
import kotlin.test.Test
import kotlin.test.assertEquals

class StartClientJourneyMapperTaxTest {
    @Test fun `recovers lead time for an existing order from detailed catalog`() {
        val orders = InMemoryOrderRepository()
        val products = object : ProductDataAccess {
            override fun getAllProducts() = "[]"
            override fun getPurchasableProducts() = "[]"
            override fun getProductById(productId: Long): String? = null
            override fun getProductBySlug(productSlug: String) = "{}"
            override fun getProductDetailsById(productId: Long) =
                """{"purchase_information":{"lead_times_and_quotation":{"general_lead_time":"2-4 Weeks"}}}"""
        }
        val snapshot = buildJsonObject {
            put("items", buildJsonArray { add(buildJsonObject { put("productId", 9091) }) })
        }

        val enriched = OrderManager(orders, products).withProductLeadTimes(snapshot)

        assertEquals("2-4 Weeks", enriched["items"]!!.jsonArray[0].jsonObject["generalLeadTime"]!!.jsonPrimitive.content)
    }

    @Test fun `uses Stripe paid amount and derived tax for email invoice`() {
        val orders = InMemoryOrderRepository()
        val mapper = StartClientJourneyMapper(orders, OrderManager(orders))
        val original = buildJsonObject {
            put("total", "126.44")
            put("taxAmount", "0.00")
            put("taxCalculation", "temporary_zero")
        }

        val paid = mapper.paidSnapshot(original, 15552, 12644, 2)

        assertEquals("126.44", paid["preTaxTotal"]!!.jsonPrimitive.content)
        assertEquals("29.08", paid["taxAmount"]!!.jsonPrimitive.content)
        assertEquals("155.52", paid["total"]!!.jsonPrimitive.content)
        assertEquals("126.44", original["total"]!!.jsonPrimitive.content)
    }
}
