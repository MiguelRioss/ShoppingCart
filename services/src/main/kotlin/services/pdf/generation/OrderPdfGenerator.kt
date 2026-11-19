package services.pdf.generation


import kotlinx.serialization.json.JsonObject
import services.order.OrderManager
import java.time.LocalDate

data class GeneratedOrderPdf(
    val filename: String,
    val bytes: ByteArray
)

/** Generates and validates the order PDFs attached to Email Sender requests. */
class OrderPdfGenerator(
    private val orders: OrderManager
) {
    fun generate(
        orderId: String,
        paidSnapshot: JsonObject,
        documentTypes: Set<String> = DOCUMENT_TYPES,
        shippingDate: LocalDate? = null
    ): Map<String, GeneratedOrderPdf> {
        require(documentTypes.isNotEmpty() && documentTypes.all(DOCUMENT_TYPES::contains)) {
            "Unsupported order PDF type"
        }
        val documents = linkedMapOf<String, GeneratedOrderPdf>()
        documentTypes.forEach { type ->
            val bytes = when (type) {
                "purchase-order" -> orders.purchaseOrderPreview(orderId, paidSnapshot)
                "invoice" -> orders.invoicePreview(orderId, paidSnapshot)
                "delivery-note" -> orders.deliveryNotePreview(orderId, paidSnapshot, shippingDate)
                "packing-list" -> orders.packingListPreview(orderId, paidSnapshot, shippingDate)
                else -> error("Unsupported order PDF type: $type")
            }
            documents[type] = GeneratedOrderPdf("$type.pdf", bytes)
        }
        documents.forEach { (type, document) ->
            require(document.bytes.size >= PDF_HEADER.size &&
                document.bytes.copyOfRange(0, PDF_HEADER.size).contentEquals(PDF_HEADER)
            ) { "$type generator did not return PDF bytes" }
        }
        return documents
    }

    private companion object {
        val DOCUMENT_TYPES = setOf("invoice", "purchase-order", "packing-list", "delivery-note")
        val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}
