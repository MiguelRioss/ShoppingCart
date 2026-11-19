package services.order

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.TestInstance
import services.pdf.generation.OrderPdfData
import productdatabaseaccesslayer.ProductDataAccess
import services.pdf.documents.DocumentTotals
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.parser.PdfTextExtractor

/** Every test reads the same paid order from a local, in-memory repository. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderPdfDataReadinessTest {
    private val sut = OrderSUT.create()

    private fun documents() = OrderInvoicePreview(sut.catalogue).build(
        sut.id,
        sut.order.getValue("snapshot").jsonObject,
        sut.manager.invoiceNumber(sut.id),
        sut.manager.invoiceIssuer(sut.id),
        requireNotNull(sut.manager.despatchEstimate(sut.id)), "TEST-TRACKING-${sut.id}", "TEST-AWB-${sut.id}"
    )

    @Test
    fun `invoice calculates net weight from tile count`() {
        val catalogue = object : ProductDataAccess by sut.catalogue {
            override fun getProductDetailsById(productId: Long): String? =
                sut.catalogue.getProductDetailsById(productId)?.replace(
                    "\"net_weight_per_tile_item\":\"\"",
                    "\"net_weight_per_tile_item\":\"0.214\""
                )
        }
        val data = OrderInvoicePreview(catalogue).build(
            sut.id, sut.order.getValue("snapshot").jsonObject,
            sut.manager.invoiceNumber(sut.id), sut.manager.invoiceIssuer(sut.id),
            requireNotNull(sut.manager.despatchEstimate(sut.id)), "TEST-TRACKING-${sut.id}", "TEST-AWB-${sut.id}"
        )
        assertEquals("34.24 kg", data.goods.single().netWeight)
        assertEquals("40 kg estimated", data.packing.grossWeight)
        val pdf = OrderPdfData.Invoice(data).generate()
        PdfReader(pdf).use { reader ->
            val content = (1..reader.numberOfPages).joinToString("\n") {
                PdfTextExtractor(reader).getTextFromPage(it)
            }
            assertContains(content, "34.24 kg")
        }
    }

    @Test
    fun `paid order has a stable system purchase order number and supplier contact`() {
        val first = sut.manager.purchaseOrderNumber(sut.id)
        assertTrue(first.startsWith("PO-"))
        assertEquals(first, sut.manager.purchaseOrderNumber(sut.id))
        val supplier = documents().parties.supplier
        assertTrue(!supplier.contactName.isNullOrBlank())
        assertTrue(!supplier.email.isNullOrBlank())
    }

    @Test
    fun `purchase order separates supplier goods packaging shipping buffer and checkout VAT`() {
        val saved = sut.order.getValue("snapshot").jsonObject
        val snapshot = JsonObject(saved + mapOf(
            "preTaxTotal" to kotlinx.serialization.json.JsonPrimitive("126.17"),
            "shippingTotal" to kotlinx.serialization.json.JsonPrimitive("39.79"),
            "taxAmount" to kotlinx.serialization.json.JsonPrimitive("29.02"),
            "total" to kotlinx.serialization.json.JsonPrimitive("155.19")
        ))
        val pdf = sut.manager.purchaseOrderPreview(sut.id, snapshot)
        PdfReader(pdf).use { reader ->
            val content = (1..reader.numberOfPages).joinToString("\n") {
                PdfTextExtractor(reader).getTextFromPage(it)
            }.replace(Regex("\\s+"), " ")
            assertContains(content, "EUR 150.00") // EUR 75/m² × 2m²
            assertContains(content, "EUR 15.00") // Catalogue packaging cost for 2m²
            assertContains(content, "EUR 39.79") // Cheapest FedEx fee with 10% buffer
            assertContains(content, "VAT (23%)")
            assertContains(content, "EUR 29.02")
            assertContains(content, "EUR 233.81")
        }
    }

    @Test
    fun `purchase order accepts the catalogue supplier purchase price field as per square metre`() {
        val supplierPriceCatalogue = object : ProductDataAccess by sut.catalogue {
            override fun getProductDetailsById(productId: Long): String? {
                val product = Json.parseToJsonElement(requireNotNull(sut.catalogue.getProductDetailsById(productId))).jsonObject
                val purchase = product.getValue("purchase_information").jsonObject
                val order = JsonObject(purchase.getValue("order").jsonObject - "supplier_price_per_m2")
                val supplier = JsonObject(product.getValue("supplier").jsonObject + mapOf(
                    "supplier_purchase_price" to kotlinx.serialization.json.JsonPrimitive("100")
                ))
                return JsonObject(product + mapOf(
                    "supplier" to supplier,
                    "purchase_information" to JsonObject(purchase + ("order" to order))
                )).toString()
            }
        }
        val snapshot = sut.order.getValue("snapshot").jsonObject
        val data = OrderInvoicePreview(supplierPriceCatalogue).build(
            sut.id, snapshot, sut.manager.invoiceNumber(sut.id), sut.manager.invoiceIssuer(sut.id),
            requireNotNull(sut.manager.despatchEstimate(sut.id)), "TEST-TRACKING-${sut.id}", "TEST-AWB-${sut.id}"
        )
        assertEquals("EUR 100.00", data.purchaseOrderFinancials?.goods?.single()?.unitPrice)
        assertEquals("EUR 200.00", data.purchaseOrderFinancials?.supplierSubtotal)
    }

    @Test
    fun `invoice uses the saved order and creates a labelled test PDF`() {
        assertEquals("PAID", sut.order.getValue("status").jsonPrimitive.content)
        val data = documents().let { it.copy(branding = it.branding.copy(subtitle = "TEST DATA - CUSTOMER AND PAYMENT SIMULATED")) }
        assertTrue(data.parties.shipFrom.addressLines.none { it != it.trim() || it.contains('\n') || it.contains('\r') })
        val pdf = OrderPdfData.Invoice(data).generate()
        assertTrue(pdf.size > 1000)
        val output = Path.of("test", "services", "order", "generated-pdfs","oneProductOrder", "invoice-test.pdf")
        Files.createDirectories(output.parent)
        Files.write(output, pdf)
        assertTrue(Files.size(output) == pdf.size.toLong())
        PdfReader(pdf).use { reader ->
            assertTrue(reader.numberOfPages > 0)
            val content = (1..reader.numberOfPages).joinToString("\n") { PdfTextExtractor(reader).getTextFromPage(it) }
            val snapshot = sut.order.getValue("snapshot").jsonObject
            val product = snapshot.getValue("items").jsonArray.first().jsonObject
            val catalogueProduct = sut.catalogue.getProductDetailsById(product.getValue("productId").jsonPrimitive.content.toLong())!!
                .let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }
            val supplier = catalogueProduct.getValue("supplier").jsonObject
            val purchase = catalogueProduct.getValue("purchase_information").jsonObject
            val productOrder = purchase.getValue("order").jsonObject
            val customs = purchase.getValue("tax_freight_and_customs").jsonObject
            val compact = content.replace(Regex("\\s+"), "")
            assertContains(content, "TEST DATA - CUSTOMER AND PAYMENT SIMULATED")
            for (value in listOf(
                sut.manager.invoiceNumber(sut.id), sut.id,
                sut.manager.invoiceIssuer(sut.id).name,
                product.getValue("name").jsonPrimitive.content,
                snapshot.getValue("total").jsonPrimitive.content,
                supplier.getValue("address_2").jsonPrimitive.content,
                productOrder.getValue("product_sku").jsonPrimitive.content,
                supplier.getValue("supplier_product_id").jsonPrimitive.content,
                customs.getValue("hs_commodity_code").jsonPrimitive.content,
                customs.getValue("country_of_origin").jsonPrimitive.content
            )) assertContains(compact, value.replace(Regex("\\s+"), ""))
            assertTrue(!content.contains("DE FERRANTI LIMITED"))
        }
    }

    @Test
    fun `purchase order uses the same saved order and reports missing reference`() {
        val incomplete = documents().copy(references = documents().references.copy(purchaseOrderNumber = ""))
        assertContains(assertFailsWith<IllegalArgumentException> { OrderPdfData.PurchaseOrder(incomplete).generate() }.message.orEmpty(), "references.purchaseOrderNumber")
    }

    @Test
    fun `packing list uses the same saved order and reports missing packing data`() {
        val original = documents()
        val incomplete = original.copy(
            references = original.references.copy(packingListNumber = "", packingDate = ""),
            packing = original.packing.copy(packageCount = "", dimensions = "")
        )
        val message = assertFailsWith<IllegalArgumentException> { OrderPdfData.PackingList(incomplete).generate() }.message.orEmpty()
        assertContains(message, "references.packingListNumber")
        assertContains(message, "references.packingDate")
        assertContains(message, "packing.packageCount")
        assertContains(message, "packing.dimensions")
    }

    @Test
    fun `delivery note uses the same saved order and reports missing tracking`() {
        val original = documents()
        val incomplete = original.copy(
            references = original.references.copy(deliveryNoteNumber = "", deliveryDate = ""),
            shipment = original.shipment.copy(trackingNumber = "")
        )
        val message = assertFailsWith<IllegalArgumentException> { OrderPdfData.DeliveryNote(incomplete).generate() }.message.orEmpty()
        assertContains(message, "references.deliveryNoteNumber")
        assertContains(message, "references.deliveryDate")
        assertContains(message, "shipment.trackingNumber")
    }

    @Test
    fun `oneProductOrder generates four PDFs`() = generateFourPdfs(OrderSUT.oneProductOrder())

    @Test
    fun `9_5m_ProductOrder generates four PDFs`() = generateFourPdfs(OrderSUT.nineAndHalfProductOrder())
}

/** Shared artifact check used by the PDF suite and by each client journey scenario. */
internal fun generateFourPdfs(multi: OrderSUT) {
        val input = Json.parseToJsonElement(
            Files.readString(Path.of("test", "services", "order", "fixtures", "fulfilment-test.json"))
        ).jsonObject
        val date = LocalDate.now().toString()
        val original = OrderInvoicePreview(multi.catalogue).build(
            multi.id, multi.order.getValue("snapshot").jsonObject,
            multi.manager.invoiceNumber(multi.id), multi.manager.invoiceIssuer(multi.id),
            requireNotNull(multi.manager.despatchEstimate(multi.id)), "TEST-TRACKING-${multi.id}", "TEST-AWB-${multi.id}"
        )
        assertEquals(multi.order.getValue("snapshot").jsonObject.getValue("cartProducts").jsonArray.size, original.goods.size)
        val data = original.copy(
            branding = original.branding.copy(subtitle = "TEST DATA - CUSTOMER, PAYMENT AND FULFILMENT SIMULATED"),
            references = original.references.copy(
                purchaseOrderNumber = "PO-TEST-${multi.id}",
                packingListNumber = "PL-TEST-${multi.id}",
                packingDate = date,
                deliveryNoteNumber = "DN-TEST-${multi.id}",
                deliveryDate = date
            ),
            shipment = original.shipment.copy(
                trackingNumber = input.getValue("trackingNumber").jsonPrimitive.content,
                awb = input.getValue("awb").jsonPrimitive.content
            )
        )
        val supplierUnitPrice = input.getValue("supplierUnitPrice").jsonPrimitive.content.toBigDecimal()
        val supplierGoods = data.goods.map { line ->
            val quantity = line.area.substringBefore(" m2").toBigDecimal()
            line.copy(
                unitPrice = "${data.totals.currency} $supplierUnitPrice",
                lineTotal = "${data.totals.currency} ${supplierUnitPrice.multiply(quantity)}"
            )
        }
        val supplierTotal = supplierGoods.sumOf { it.lineTotal!!.substringAfter(' ').toBigDecimal() }
        val purchaseOrder = data.copy(
            goods = supplierGoods,
            totals = DocumentTotals(
                "${data.totals.currency} $supplierTotal",
                "${data.totals.currency} ${BigDecimal.ZERO}",
                "${data.totals.currency} $supplierTotal",
                data.totals.currency
            )
        )
        val output = Path.of("test", "services", "order", "generated-pdfs", multi.scenarioName)
        Files.createDirectories(output)
        val documents = mapOf(
            "invoice.pdf" to OrderPdfData.Invoice(data).generate(),
            "purchase-order.pdf" to OrderPdfData.PurchaseOrder(purchaseOrder).generate(),
            "packing-list.pdf" to OrderPdfData.PackingList(data).generate(),
            "delivery-note.pdf" to OrderPdfData.DeliveryNote(data).generate()
        )
        documents.forEach { (name, bytes) ->
            val path = output.resolve(name)
            Files.write(path, bytes)
            assertTrue(Files.size(path) > 1000, "$name missing")
            PdfReader(bytes).use { reader ->
                val text = (1..reader.numberOfPages).joinToString("\n") { PdfTextExtractor(reader).getTextFromPage(it) }
                assertContains(text, "TEST DATA - CUSTOMER, PAYMENT AND FULFILMENT SIMULATED")
                val compact = text.replace(Regex("\\s+"), "")
                data.goods.forEach { line ->
                    assertContains(compact, line.productName.replace(Regex("\\s+"), ""))
                }
            }
            println("GENERATED_ORDER_PDF=${path.toAbsolutePath()}")
        }
}
