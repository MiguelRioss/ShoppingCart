package services.order

import java.util.Base64
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import services.orderemail.EmailSenderApi
import services.orderemail.PaidOrderEmailPayloadMapper
import services.orderemail.PurchaseCompleteEmailSender

class StartClientJourneyTest {
    @Test
    fun `9_5m_ProductOrder client journey`() {
        val sut = OrderSUT.nineAndHalfProductOrder()
        val areas = linkedMapOf(9091L to BigDecimal("3.0"), 9095L to BigDecimal("3.5"), 9093L to BigDecimal("3.0"))
        val request = PaidOrderEmailPayloadMapper(sut.orders, sut.manager).fromPaidCheckout(sut.id)
        val order = request.getValue("order").jsonObject
        val snapshot = order.getValue("snapshot").jsonObject
        val cart = snapshot.getValue("cartProducts").jsonArray.map { it.jsonObject }
        assertEquals(areas.keys.map(Long::toString), cart.map { it.getValue("productId").jsonPrimitive.content })
        assertEquals(BigDecimal("9.5"), cart.sumOf { it.getValue("squareMeters").jsonPrimitive.content.toBigDecimal() })
        assertEquals(listOf("6", "7", "6"), cart.map { it.getValue("amountBoxes").jsonPrimitive.content })
        assertEquals(3, snapshot.getValue("items").jsonArray.size)
        assertTrue(snapshot.getValue("total").jsonPrimitive.content.toBigDecimal().compareTo(BigDecimal("1710")) == 0)
        assertEquals("171000", order.getValue("amountPaidMinor").jsonPrimitive.content)
        val invoice = request.getValue("documents").jsonObject.getValue("invoice").jsonObject
        val pdf = Base64.getDecoder().decode(invoice.getValue("contentBase64").jsonPrimitive.content)
        org.openpdf.text.pdf.PdfReader(pdf).use { reader ->
            val content = (1..reader.numberOfPages).joinToString("\n") { org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(it) }
            for (item in snapshot.getValue("items").jsonArray)
                assertTrue(content.replace(Regex("\\s+"), "").contains(item.jsonObject.getValue("name").jsonPrimitive.content.replace(Regex("\\s+"), "")))
        }
        writeJourneyJson(sut, request)
        generateFourPdfs(sut)
    }

    @Test
    fun `oneProductOrder client journey`() {
        val sut = OrderSUT.oneProductOrder(paid = false)
        val mapper = PaidOrderEmailPayloadMapper(sut.orders, sut.manager)
        assertFailsWith<IllegalArgumentException> { mapper.fromPaidCheckout(sut.id) }
        val snapshot = sut.order.getValue("snapshot").jsonObject
        val amount = snapshot.getValue("total").jsonPrimitive.content.toBigDecimal().movePointRight(2).longValueExact()
        sut.orders.updatePayment(sut.id, "PAID", amount, snapshot.getValue("currency").jsonPrimitive.content, null)
        sut.orders.claimShipmentBooking(sut.id, "origin-1")
        sut.orders.completeShipmentBooking(sut.id, "origin-1", kotlinx.serialization.json.buildJsonObject {
            put("provider", kotlinx.serialization.json.JsonPrimitive("FEDEX"))
            put("masterTrackingNumber", kotlinx.serialization.json.JsonPrimitive("TEST-AWB-${sut.id}"))
            put("packages", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("trackingNumber", kotlinx.serialization.json.JsonPrimitive("TEST-TRACKING-${sut.id}"))
                })
            })
        })
        val calls = mutableListOf<JsonObject>()
        PurchaseCompleteEmailSender(mapper, object : EmailSenderApi {
            override fun startPurchaseComplete(payload: JsonObject): JsonObject {
                calls += payload
                return JsonObject(emptyMap())
            }

            override fun sendEmail(payload: JsonObject): JsonObject = error("Fulfilment email not expected here")
        }).onPaid(sut.id)

        val request = calls.single()
        val source = requireNotNull(sut.orders.findByCheckoutForEmail(sut.id))
        val order = request.getValue("order").jsonObject
        val sentSnapshot = order.getValue("snapshot").jsonObject
        assertEquals(source.getValue("snapshot").jsonObject.getValue("total"), sentSnapshot.getValue("preTaxTotal"))
        assertEquals(source.getValue("snapshot").jsonObject.getValue("total"), sentSnapshot.getValue("total"))
        assertEquals("0", sentSnapshot.getValue("taxAmount").jsonPrimitive.content)
        for (field in listOf("id", "checkoutSessionId", "status", "paidAt", "paidCurrency", "amountPaidMinor"))
            assertEquals(source.getValue(field), order.getValue(field), field)
        assertEquals(sut.manager.invoiceNumber(sut.id), order.getValue("invoiceNumber").jsonPrimitive.content)
        assertEquals("PO-${sut.id}", order.getValue("purchaseOrderNumber").jsonPrimitive.content)
        val invoice = request.getValue("documents").jsonObject.getValue("invoice").jsonObject
        assertEquals(order.getValue("invoiceNumber"), invoice.getValue("number"))
        assertEquals("invoice.pdf", invoice.getValue("filename").jsonPrimitive.content)
        assertEquals("application/pdf", invoice.getValue("contentType").jsonPrimitive.content)
        val bytes = Base64.getDecoder().decode(invoice.getValue("contentBase64").jsonPrimitive.content)
        assertTrue(bytes.size > 1000)
        assertEquals("%PDF-", bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII))
        val documents = request.getValue("documents").jsonObject
        assertEquals(setOf("purchase-order", "delivery-note", "invoice", "packing-list"), documents.keys)
        for ((type, filename) in mapOf(
            "purchase-order" to "purchase-order.pdf",
            "delivery-note" to "delivery-note.pdf",
            "invoice" to "invoice.pdf",
            "packing-list" to "packing-list.pdf"
        )) {
            val document = documents.getValue(type).jsonObject
            assertEquals(filename, document.getValue("filename").jsonPrimitive.content)
            assertEquals("application/pdf", document.getValue("contentType").jsonPrimitive.content)
            val documentBytes = Base64.getDecoder().decode(document.getValue("contentBase64").jsonPrimitive.content)
            assertEquals("%PDF-", documentBytes.copyOfRange(0, 5).toString(Charsets.US_ASCII))
        }
        val deliveryNote = documents.getValue("delivery-note").jsonObject
        val deliveryBytes = Base64.getDecoder().decode(deliveryNote.getValue("contentBase64").jsonPrimitive.content)
        org.openpdf.text.pdf.PdfReader(deliveryBytes).use { reader ->
            val content = (1..reader.numberOfPages).joinToString("\n") {
                org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(it)
            }
            val compact = content.replace(Regex("\\s+"), "")
            assertTrue(compact.contains("TEST-AWB-${sut.id}"))
            assertTrue(compact.contains("TEST-TRACKING-${sut.id}"))
            assertFalse(content.contains("Pending"))
        }
        assertFalse(request.containsKey("templates"))
        assertFalse(request.containsKey("missing"))
        writeJourneyJson(sut, request)
        generateFourPdfs(sut)
    }

    private fun scenarioOutput(sut: OrderSUT): Path =
        Path.of("test", "services", "order", "generated-pdfs", sut.scenarioName).also(Files::createDirectories)

    private fun writeJourneyJson(sut: OrderSUT, request: JsonObject) {
        val output = scenarioOutput(sut).resolve("client-journey.json")
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), request))
        println("START_CLIENT_JOURNEY_JSON=${output.toAbsolutePath()}")
    }
}
