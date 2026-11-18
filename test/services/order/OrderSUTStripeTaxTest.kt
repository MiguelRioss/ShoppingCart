package services.order

import config.Environment
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OrderSUTStripeTaxTest {
    @Test
    fun `Portuguese test account calculates VAT for OrderSUT destinations`() {
        val key = Environment.get("STRIPE_PT_SECRET_KEY")
        assumeTrue(key?.startsWith("sk_test_") == true)

        val order = OrderSUT.oneProductOrder()
        val snapshot = order.order.getValue("snapshot").jsonObject
        val amount = snapshot.getValue("total").jsonPrimitive.content.toBigDecimal()
        val amountMinor = amount.movePointRight(2).longValueExact()
        val expectedTax = amount.multiply(BigDecimal("0.23")).movePointRight(2).longValueExact()
        val commonFields = mapOf(
            "currency" to "eur",
            "line_items[0][amount]" to amountMinor.toString(),
            "line_items[0][reference]" to order.id,
            "line_items[0][tax_behavior]" to "exclusive",
            "customer_details[address_source]" to "shipping"
        )
        val scenarios = listOf(
            Triple("PT", "1000-001", expectedTax),
            Triple("IT", "20121", expectedTax),
            Triple("US", "10001", 0L)
        )
        scenarios.forEach { (country, postalCode, expectedAmount) ->
            val fields = commonFields + mapOf(
                "customer_details[address][country]" to country,
                "customer_details[address][postal_code]" to postalCode
            )
            val form = fields.entries.joinToString("&") { (name, value) ->
                "${URLEncoder.encode(name, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
            }
            val request = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/tax/calculations"))
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode(), "$country: ${response.body()}")
            val calculation = Json.parseToJsonElement(response.body()).jsonObject
            assertEquals("false", calculation.getValue("livemode").jsonPrimitive.content)
            assertEquals(expectedAmount, calculation.getValue("tax_amount_exclusive").jsonPrimitive.content.toLong(), country)
            assertEquals(
                amountMinor + expectedAmount,
                calculation.getValue("amount_total").jsonPrimitive.content.toLong(),
                "$country total payable including VAT"
            )
        }
    }
}
