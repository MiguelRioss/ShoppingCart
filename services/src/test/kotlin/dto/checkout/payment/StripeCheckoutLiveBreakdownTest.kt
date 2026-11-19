package dto.checkout.payment.stripe

import config.Environment
import domain.checkout.CheckoutLineItem
import domain.checkout.CheckoutDeliveryAddress
import domain.checkout.payment.CheckoutPaymentRequest
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import services.checkout.payment.stripe.StripeConfig
import services.checkout.payment.stripe.StripePaymentProvider
import kotlin.test.assertEquals

class StripeCheckoutLiveBreakdownTest {
    @Test
    fun `registered customer address removes shipping form and calculates VAT`() {
        val key = Environment.get("STRIPE_PT_SECRET_KEY")
        assumeTrue(key?.startsWith("sk_test_") == true)
        val provider = StripePaymentProvider(StripeConfig(key!!, "https://api.stripe.com/v1/checkout/sessions"))
        val checkout = provider.createCheckoutSession(CheckoutPaymentRequest(
            cartId = "address-prefill-${java.util.UUID.randomUUID()}",
            sessionId = "address-prefill-test",
            successUrl = "https://example.com/success",
            cancelUrl = "https://example.com/cancel",
            customerEmail = "address-test@example.com",
            destinationCountry = "PT",
            customerName = "Test Buyer",
            deliveryAddress = CheckoutDeliveryAddress(null, "Rua de Teste 1", null, "Lisboa", "1000-001", "PT"),
            lineItems = listOf(
                CheckoutLineItem(1, "Test product", 1, BigDecimal("100.00")),
                CheckoutLineItem(0, "Shipping", 1, BigDecimal("10.00"))
            )
        ))
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/checkout/sessions/${checkout.id}"))
                .header("Authorization", "Bearer $key").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, response.statusCode(), response.body())
        val session = Json.parseToJsonElement(response.body()).jsonObject
        assertEquals("true", session.getValue("automatic_tax").jsonObject.getValue("enabled").jsonPrimitive.content)
        assertEquals("null", session.getValue("shipping_address_collection").toString())
        assertEquals("1000", session.getValue("total_details").jsonObject.getValue("amount_shipping").jsonPrimitive.content)
        assertEquals("2530", session.getValue("total_details").jsonObject.getValue("amount_tax").jsonPrimitive.content)
        assertEquals("13530", session.getValue("amount_total").jsonPrimitive.content)
        val customerId = session.getValue("customer").jsonPrimitive.content
        val customerResponse = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/customers/$customerId"))
                .header("Authorization", "Bearer $key").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, customerResponse.statusCode())
        val customer = Json.parseToJsonElement(customerResponse.body()).jsonObject
        assertEquals("Rua de Teste 1", customer.getValue("shipping").jsonObject
            .getValue("address").jsonObject.getValue("line1").jsonPrimitive.content)
    }

    @Test
    fun `Stripe accepts separate product shipping and automatic VAT`() {
        val key = Environment.get("STRIPE_PT_SECRET_KEY")
        assumeTrue(key?.startsWith("sk_test_") == true)
        val request = StripeCheckoutRequest.from(CheckoutPaymentRequest(
            cartId = "vat-breakdown-test",
            sessionId = "vat-breakdown-test",
            successUrl = "https://example.com/success",
            cancelUrl = "https://example.com/cancel",
            destinationCountry = "PT",
            lineItems = listOf(
                CheckoutLineItem(1, "Test product", 1, BigDecimal("100.00")),
                CheckoutLineItem(0, "Shipping", 1, BigDecimal("10.00"))
            )
        ))
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(request.toFormBody()))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, response.statusCode(), response.body())
        val session = Json.parseToJsonElement(response.body()).jsonObject
        assertEquals("true", session.getValue("automatic_tax").jsonObject.getValue("enabled").jsonPrimitive.content)
        assertEquals("10000", session.getValue("amount_subtotal").jsonPrimitive.content)
        assertEquals("1000", session.getValue("total_details").jsonObject.getValue("amount_shipping").jsonPrimitive.content)
        assertEquals(1, session.getValue("shipping_options").jsonArray.size)
    }
}
