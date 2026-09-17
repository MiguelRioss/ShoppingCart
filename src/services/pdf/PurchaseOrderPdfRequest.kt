package services.pdf

data class PurchaseOrderPdfRequest(
    val logoPath: String,
    val purchaseOrderNumber: String,
    val purchaseOrderDate: String,
    val customerOrderNumber: String,
    val requiredDespatchDate: String,
    val purchaser: PurchaseOrderParty,
    val supplier: PurchaseOrderParty,
    val delivery: PurchaseOrderParty,
    val lines: List<PurchaseOrderLine>,
    val totals: PurchaseOrderTotals,
    val packingLines: List<String>,
    val directShipmentInstructionLines: List<String>,
    val requiredDocumentLines: List<String>,
    val supplierConfirmationLines: List<String>,
    val authorisationLines: List<String>,
    val controlNote: String,
    val subtitle: String? = null,
    val footerText: String = "DE FERRANTI LIMITED | ask@deferranti.com | www.deferranti.com"
)

data class PurchaseOrderParty(
    val name: String,
    val lines: List<String>
)

data class PurchaseOrderLine(
    val productName: String,
    val specificationLines: List<String>,
    val area: String,
    val boxes: String,
    val tiles: String,
    val unitPrice: String,
    val lineTotal: String,
    val unitPriceLabel: String = "per m2",
    val deFerrantiSku: String? = null,
    val supplierProductId: String? = null
)

data class PurchaseOrderTotals(
    val subtotal: String,
    val vat: String,
    val total: String
)
