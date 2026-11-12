package dto.checkout.payment.stripe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StripeCheckoutTaxResponseTest {
    @Test
    fun `reads tax amounts from simulated Stripe responses`() {
        val scenarios = listOf(
            "positive tax" to 2300L,
            "zero tax" to 0L
        )

        scenarios.forEach { (scenario, expectedTax) ->
            val response = StripeCheckoutResponse.fromJson(
                """{"id":"cs_test_123","status":"open","total_details":{"amount_tax":$expectedTax}}"""
            )
            assertEquals(expectedTax, response.amountTax, scenario)
        }
    }

    @Test
    fun `keeps tax unknown when Stripe has not calculated it`() {
        val response = StripeCheckoutResponse.fromJson("""{"id":"cs_test_123","status":"open"}""")
        assertNull(response.amountTax)
    }
}
