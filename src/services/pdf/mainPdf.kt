import productdatabaseaccesslayer.ProductCatalogDataSource
import services.pdf.CommercialInvoicePdf
import services.pdf.DeliveryNotePdf
import services.pdf.PackingListPdf
import services.pdf.PurchaseOrderPdf
import services.pdf.RealProductTestOrderDocuments

/**
 * Manual PDF smoke-test entry point.
 *
 * It reads one real catalogue product, prints calculated values, and writes all supported test
 * documents to the current directory. This function is for local verification, not application
 * startup or production document generation.
 */
fun main() {

    val productDataAccess =
        ProductCatalogDataSource()

    val documents =
        RealProductTestOrderDocuments(
            productDataAccess = productDataAccess
        ).create(
            productId = 9278,
            quantityM2 = 9.5
        )

    println("Unit price: ${documents.goods.first().unitPrice}")
    println("Line total: ${documents.goods.first().lineTotal}")
    println("Subtotal: ${documents.totals.supplierSubtotal}")
    println("VAT: ${documents.totals.vatTreatment}")
    println("Total: ${documents.totals.purchaseOrderTotal}")

    PurchaseOrderPdf().generateToFile(
        documents = documents,
        outputPath = "test-purchase-order.pdf"
    )

    PackingListPdf().generateToFile(
        documents = documents,
        outputPath = "test-packing-list.pdf"
    )

    DeliveryNotePdf().generateToFile(
        documents = documents,
        outputPath = "test-delivery-note.pdf"
    )
    CommercialInvoicePdf().generateToFile(
        documents = documents,
        outputPath = "test-commercial-invoice.pdf"
    )

    println("PDFs created using real product data")
}
