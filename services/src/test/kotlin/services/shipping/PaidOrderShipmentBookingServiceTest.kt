package services.shipping

import services.shipping.fulfillment.PaidOrderShipmentBookingService

import db.offline.InMemoryOrderRepository
import domain.shipment.Shipment
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentRateRequest
import domain.shipment.ShipmentRequest
import domain.shipment.ShipmentQuote
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import shipment.core.ProviderAccount
import shipment.core.ProviderAuthToken
import shipment.core.ProviderCredentials
import shipment.core.ShipmentProviderService
import shipment.core.ShipmentProviderType

class PaidOrderShipmentBookingServiceTest {
    @Test
    fun `paid sandbox checkout books once and exposes the AWB`() {
        val orders = InMemoryOrderRepository()
        val checkoutId = "cs_test_shipment"
        orders.create(checkoutId, null, snapshot())
        orders.updatePayment(checkoutId, "PAID", 9000, "eur", "pi_test")
        var calls = 0
        val carrier = fakeCarrier { request ->
            calls++
            assertEquals("FEDEX_PRIORITY", request.serviceType)
            assertEquals("Supplier Contact", request.shipperContact.personName)
            assertEquals("+351910000000", request.recipientContact.phoneNumber)
            Shipment(
                ShipmentProviderType.FEDEX, "shipment-1", "794644790132", request.serviceType,
                "2026-11-16", emptyList(), emptyList(), emptyList()
            )
        }
        val service = PaidOrderShipmentBookingService(orders, mapper(validateWeight = true), carrier) { "SANDBOX" }

        service.bookPaidOrder(checkoutId)
        service.bookPaidOrder(checkoutId)

        assertEquals(1, calls)
        val booking = orders.shipmentBookings(checkoutId).single().jsonObject
        assertEquals("BOOKED", booking.getValue("status").jsonPrimitive.content)
        assertEquals("794644790132", booking.getValue("masterTrackingNumber").jsonPrimitive.content)
    }

    @Test
    fun `does not book a shipment outside explicit sandbox mode`() {
        val orders = InMemoryOrderRepository()
        val checkoutId = "cs_test_no_live_booking"
        orders.create(checkoutId, null, snapshot())
        orders.updatePayment(checkoutId, "PAID", 9000, "eur", "pi_test")
        val carrier = fakeCarrier { error("Must not book") }
        PaidOrderShipmentBookingService(orders, mapper(), carrier) { "PRODUCTION" }.bookPaidOrder(checkoutId)
        assertTrue(orders.shipmentBookings(checkoutId).isEmpty())
    }

    @Test
    fun `rejects impossible catalogue gross weight before calling FedEx`() {
        val orders = InMemoryOrderRepository()
        val checkoutId = "cs_test_bad_weight"
        orders.create(checkoutId, null, snapshot())
        orders.updatePayment(checkoutId, "PAID", 9000, "eur", "pi_test")
        var calls = 0
        val carrier = fakeCarrier { calls++; error("Must not book impossible package") }
        PaidOrderShipmentBookingService(orders, mapper(validateWeight = true, grossWeight = "8.2"), carrier) { "SANDBOX" }
            .bookPaidOrder(checkoutId)
        assertEquals(0, calls)
        val review = orders.shipmentBookings(checkoutId).single().jsonObject
        assertEquals("preflight", review.getValue("originKey").jsonPrimitive.content)
        assertEquals("NEEDS_REVIEW", review.getValue("status").jsonPrimitive.content)
    }

    private fun mapper(validateWeight: Boolean = false, grossWeight: String = "8.6") = ProductShipmentRequestMapper(object : ProductDataAccess {
        override fun getAllProducts() = "[]"
        override fun getPurchasableProducts() = "[]"
        override fun getProductBySlug(productSlug: String) = "{}"
        override fun getProductById(productId: Long): String? = getProductDetailsById(productId)
        override fun getProductDetailsById(productId: Long) = productJson.replace("PACKED_WEIGHT", grossWeight)
    }, validateGrossVsCalculatedNet = validateWeight)

    private fun fakeCarrier(create: (ShipmentRequest) -> Shipment) = object : ShipmentProviderService {
        override val type = ShipmentProviderType.FEDEX
        override val account = ProviderAccount("test-account", "PT")
        override val credentials = ProviderCredentials("test-client", "test-secret")
        override fun login() = ProviderAuthToken("test-token")
        override fun getQuotes(request: ShipmentRateRequest): List<ShipmentQuote> = emptyList()
        override fun createShipment(request: ShipmentRequest) = create(request)
    }

    private fun snapshot() = buildJsonObject {
        put("cartId", UUID.randomUUID().toString())
        put("cartUpdatedAt", "2026-11-16T12:00:00")
        put("customerFirstName", "Test Customer")
        put("customerPhone", "+351910000000")
        put("currency", "eur")
        put("shipping", buildJsonObject {
            put("originServiceTypes", buildJsonArray { add("FEDEX_PRIORITY") })
        })
        put("deliveryAddress", buildJsonObject {
            put("addressLine1", "1 Test Street"); put("townOrCity", "Lisbon")
            put("postcode", "1000-001"); put("country", "PT")
        })
        put("cartProducts", buildJsonArray { add(buildJsonObject {
            put("productId", 9091); put("squareMeters", 0.5); put("amountBoxes", 1)
            put("totalPricePerProduct", "90.00"); put("isSample", false)
        }) })
    }

    private val productJson = """
        {"supplier":{"name":"Test Supplier","contact_name":"Supplier Contact","phone":"+351911111111",
        "address_2":"Rua 1","city_collection":"Lisbon","post_code_collection":"1000-001","country_collection":"Portugal"},
        "purchase_information":{"order":{"units_per_box":"40","net_weight_per_tile_item":"0.214"},
        "shipping":{"packing_size":[{"quantity":"0.5","packed_weight":"PACKED_WEIGHT","length":"30","width":"30","height":"20"}]},
        "tax_freight_and_customs":{"customs_description":"Tile","country_of_origin":"Portugal"}}}
    """.trimIndent()
}
