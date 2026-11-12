package services.orderemail

import db.offline.InMemoryOrderRepository
import kotlinx.serialization.json.*
import services.order.OrderManager
import kotlin.test.Test
import kotlin.test.assertEquals

class StartClientJourneyMapperTaxTest {
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
