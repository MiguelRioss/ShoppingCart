package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RetryingFedExRateQuotationClientTest {
    @Test
    fun `retries until delegate returns a successful response`() {
        val delegate = FailingThenSuccessfulClient(
            failuresBeforeSuccess = 2
        )
        val retryingClient = RetryingFedExRateQuotationClient(
            delegate = delegate,
            maxAttempts = 5,
            delayMillis = { 0 },
            sleep = {}
        )

        val response = retryingClient.quote(testRequest())

        assertEquals(3, delegate.attempts)
        assertEquals("ok", response["status"].toString().trim('"'))
    }

    @Test
    fun `fails after max attempts when delegate never succeeds`() {
        val delegate = FailingThenSuccessfulClient(
            failuresBeforeSuccess = Int.MAX_VALUE
        )
        val retryingClient = RetryingFedExRateQuotationClient(
            delegate = delegate,
            maxAttempts = 3,
            delayMillis = { 0 },
            sleep = {}
        )

        val error = assertFailsWith<IllegalStateException> {
            retryingClient.quote(testRequest())
        }

        assertEquals(3, delegate.attempts)
        assertEquals(
            "FedEx rate quotation did not return 200 OK after 3 attempts",
            error.message
        )
    }

    private fun testRequest() =
        FedExQuoteRequest(
            originAddress = address(),
            destinationAddress = address(),
            packageSize = ShippingPackageSize(
                quantity = BigDecimal("1.0"),
                length = BigDecimal("10"),
                width = BigDecimal("10"),
                height = BigDecimal("10"),
                packedWeight = BigDecimal("1.0"),
                packagingCost = null,
                packaging = "Box(s)"
            ),
            incoterm = null
        )

    private fun address() =
        ShippingAddress(
            streetLines = listOf("Street"),
            city = "City",
            postalCode = "Postal",
            countryCode = "GB"
        )

    private class FailingThenSuccessfulClient(
        private val failuresBeforeSuccess: Int
    ) : FedExRateQuotationClient {

        var attempts: Int = 0
            private set

        override fun quote(
            request: FedExQuoteRequest
        ) =
            if (++attempts <= failuresBeforeSuccess) {
                throw IllegalStateException("FedEx did not return 200 OK")
            } else {
                buildJsonObject {
                    put("status", "ok")
                }
            }
    }
}
