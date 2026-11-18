package services.shipping

import services.shipping.rates.normalizeOptionalDuties

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OptionalFedExDutiesTest {
    private val response = """{"output":{"rateReplyDetails":[{"ratedShipmentDetails":[{"rateType":"ACCOUNT","totalBaseCharge":10,"totalNetCharge":12,"totalDiscounts":0,"shipmentRateDetail":{"totalSurcharges":2},"currency":"EUR"}]}]}}"""

    @Test
    fun `domestic and intra EU missing duties are accepted`() {
        listOf("PT" to "PT", "PT" to "ES").forEach { (origin, destination) ->
            val normalized = normalizeOptionalDuties(response, origin, destination)
            val output = Json.parseToJsonElement(normalized).jsonObject["output"]!!.jsonObject
            val amount = output["rateReplyDetails"]!!.jsonArray[0].jsonObject
                .getValue("ratedShipmentDetails").jsonArray[0].jsonObject
                .getValue("totalDutiesAndTaxes").jsonPrimitive.content
            assertEquals("0", amount)
        }
    }

    @Test
    fun `non EU missing duties remain an error`() {
        assertEquals(response, normalizeOptionalDuties(response, "PT", "US"))
    }

    @Test
    fun `existing duties are preserved`() {
        val withDuties = response.replace("\"rateType\":\"ACCOUNT\"", "\"rateType\":\"ACCOUNT\",\"totalDutiesAndTaxes\":17")
        assertEquals(withDuties, normalizeOptionalDuties(withDuties, "PT", "PT"))
    }
}
