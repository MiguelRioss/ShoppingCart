package services.pdf

/**
 * Shared data used to generate the De Ferranti fulfilment document set:
 * purchase order, packing list, and delivery note.
 *
 * This class intentionally contains document data only. The individual
 * PDF generators will decide how to lay these values out.
 */
data class DeFerrantiOrderDocuments(
    val branding: DocumentBranding,
    val references: DocumentReferences,
    val parties: DocumentParties,
    val goods: List<DocumentGoodsLine>,
    val totals: DocumentTotals,
    val packing: PackingDetails,
    val shipment: ShipmentDetails,
    val checks: DocumentChecks,
    val notes: DocumentNotes
)

data class DocumentBranding(
    val logoPath: String,
    val footerText: String = "DE FERRANTI LIMITED | ask@deferranti.com | www.deferranti.com",
    val subtitle: String? = null
)

data class DocumentReferences(
    val purchaseOrderNumber: String,
    val purchaseOrderDate: String,
    val customerOrderNumber: String,
    val requiredDespatchDate: String,
    val packingListNumber: String,
    val packingDate: String,
    val deliveryNoteNumber: String,
    val deliveryDate: String,
    val invoiceNumber: String? = null
)

data class DocumentParties(
    val purchaser: DocumentParty,
    val supplier: DocumentParty,
    val shipFrom: DocumentParty,
    val deliverTo: DocumentParty,
    val importerOfRecord: DocumentParty,
    val seller: DocumentParty
)

data class DocumentParty(
    val name: String,
    val addressLines: List<String>,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val customerType: String? = null,
    val vatNumber: String? = null,
    val eoriNumber: String? = null
) {

    fun lines(
        includeContact: Boolean = true,
        includeCustomerType: Boolean = false
    ): List<String> =
        listOfNotNull(
            customerType
                ?.takeIf { includeCustomerType }
                ?.let { "Customer type: $it" },
            contactName
                ?.takeIf { includeContact }
                ?.let { "Attn: $it" }
        ) +
                addressLines +
                listOfNotNull(
                    vatNumber?.let { "VAT: $it" },
                    eoriNumber?.let { "EORI: $it" },
                    phone?.let { "T: $it" },
                    email?.let { "E: $it" }
                )
}

data class DocumentGoodsLine(
    val productName: String,
    val specificationLines: List<String>,
    val area: String,
    val boxes: String,
    val tiles: String,
    val unitPrice: String? = null,
    val lineTotal: String? = null,
    val unitPriceLabel: String = "per m2",
    val deFerrantiSku: String? = null,
    val supplierProductId: String? = null,
    val hsCode: String? = null,
    val originCountry: String? = null,
    val netWeight: String? = null,
    val grossWeight: String? = null,
    val packageDescription: String? = null,
    val standardisedProductId: String? = null
)

data class DocumentTotals(
    val supplierSubtotal: String,
    val vatTreatment: String,
    val purchaseOrderTotal: String
)

data class PackingDetails(
    val packageType: String,
    val packageCount: String,
    val dimensions: String,
    val grossWeight: String,
    val contents: String,
    val packingCost: String? = null,
    val handlingLines: List<String> = emptyList()
)

data class ShipmentDetails(
    val courier: String,
    val service: String,
    val trackingNumber: String,
    val awb: String,
    val purpose: String,
    val incoterm: String,
    val deliveryTerms: String
)

data class DocumentChecks(
    val supplierConfirmationLines: List<String>,
    val authorisationLines: List<String>,
    val packedByLines: List<String>,
    val finalCheckLines: List<String>,
    val deliveryConditionLines: List<String>,
    val deliveryExceptionLines: List<String>,
    val receivedByLines: List<String>,
    val courierReceiptLines: List<String>
)

data class DocumentNotes(
    val requiredDocumentLines: List<String>,
    val b2bB2cLines: List<String>,
    val deliveryImportantLines: List<String>,
    val documentMatchLines: List<String>,
    val purchaseOrderControlNote: String,
    val packingListControlNote: String,
    val deliveryNoteControlNote: String
)
