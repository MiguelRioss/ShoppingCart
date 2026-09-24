package services.pdf

/**
 * Presentation-ready data required to create a supplier purchase order.
 *
 * Values are already formatted for display, including dates, quantities, and money. This
 * keeps the PDF builder focused on layout and leaves calculation and localisation to the
 * application layer that creates the request.
 */
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

/** A named organisation or person and the address/contact lines shown below its name. */
data class PurchaseOrderParty(
    val name: String,
    val lines: List<String>
)

/**
 * One product line displayed on a purchase order.
 *
 * Optional identifiers are omitted from the document when unavailable. Quantity and monetary
 * fields are strings because they must arrive in their final display format.
 */
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

/** Display-formatted financial summary for the purchase order. */
data class PurchaseOrderTotals(
    val subtotal: String,
    val vat: String,
    val total: String
)
