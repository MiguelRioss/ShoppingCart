package services.pdf

import java.io.File
import services.pdf.model.OpenPdfGenerator
import services.pdf.model.PdfCell
import services.pdf.model.PdfDocument
import services.pdf.model.PdfFooter
import services.pdf.model.PdfHeader
import services.pdf.model.PdfRow
import services.pdf.model.PdfTable
import services.pdf.model.PdfTextType

class PurchaseOrderPdf(
    private val generator: PdfGenerator = OpenPdfGenerator()
) {

    fun generate(request: PurchaseOrderPdfRequest): ByteArray =
        generator.generate(createDocument(request))

    fun generate(documents: DeFerrantiOrderDocuments): ByteArray =
        generate(documents.toPurchaseOrderRequest())

    fun generateToFile(
        request: PurchaseOrderPdfRequest,
        outputPath: String
    ) {
        File(outputPath).writeBytes(generate(request))
    }

    fun generateToFile(
        documents: DeFerrantiOrderDocuments,
        outputPath: String
    ) {
        generateToFile(
            request = documents.toPurchaseOrderRequest(),
            outputPath = outputPath
        )
    }

    fun createDocument(request: PurchaseOrderPdfRequest): PdfDocument =
        PdfDocument(
            header =
                PdfHeader(
                    logoPath = request.logoPath,
                    title = "PURCHASE ORDER",
                    subtitle = request.subtitle
                ),
            elements = listOf(
                orderInfo(request),
                parties(request),
                products(request),
                totals(request),
                PdfTable.equalColumns(
                    PdfCell.titled("PACKING AND DESPATCH", request.packingLines),
                    PdfCell.titled(
                        "DIRECT SHIPMENT INSTRUCTIONS",
                        request.directShipmentInstructionLines
                    ),
                    PdfCell.titled("DOCUMENTS REQUIRED", request.requiredDocumentLines)
                ),
                PdfTable.equalColumns(
                    PdfCell.titled("SUPPLIER CONFIRMATION", request.supplierConfirmationLines),
                    PdfCell.titled("DE FERRANTI AUTHORISATION", request.authorisationLines)
                ),
                PdfTable.withWidths(
                    listOf(1f),
                    PdfRow.normal(
                        PdfCell.text(request.controlNote, PdfTextType.SMALL)
                    )
                )
            ),
            footer = PdfFooter(request.footerText)
        )

    private fun orderInfo(request: PurchaseOrderPdfRequest): PdfTable =
        PdfTable.withWidths(
            listOf(1f, 1f, 1f, 1f),
            PdfRow.total(
                PdfCell.label("PURCHASE ORDER"),
                PdfCell.label("PO DATE"),
                PdfCell.label("CUSTOMER ORDER"),
                PdfCell.label("REQUIRED DESPATCH")
            ),
            PdfRow.normal(
                PdfCell.text(request.purchaseOrderNumber),
                PdfCell.text(request.purchaseOrderDate),
                PdfCell.text(request.customerOrderNumber),
                PdfCell.text(request.requiredDespatchDate)
            )
        )

    private fun parties(request: PurchaseOrderPdfRequest): PdfTable =
        PdfTable.withWidths(
            listOf(1.05f, 1.05f, 1f),
            PdfRow.normal(
                partyCell("PURCHASER", request.purchaser),
                partyCell("SUPPLIER / FULFILMENT PARTY", request.supplier),
                partyCell("DELIVER DIRECTLY TO", request.delivery)
            )
        )

    private fun products(request: PurchaseOrderPdfRequest): PdfTable =
        PdfTable.withWidths(
            0.7f,
            4.05f,
            1f,
            1f,
            1f,
            1.2f,
            1.3f,
            rows = listOf(productHeader()) +
                request.lines.mapIndexed { index, line ->
                    PurchaseOrderProductBox.row(
                        lineNumber = index + 1,
                        line = line
                    )
                }
        )

    private fun productHeader(): PdfRow =
        PdfRow.header(
            PdfCell.header("LINE"),
            PdfCell.header("PRODUCT / SPECIFICATION"),
            PdfCell.header("AREA"),
            PdfCell.header("BOXES"),
            PdfCell.header("TILES"),
            PdfCell.header("UNIT PRICE"),
            PdfCell.header("LINE TOTAL")
        )

    private fun totals(request: PurchaseOrderPdfRequest): PdfTable =
        PdfTable.withWidths(
            listOf(4f, 1.8f),
            amount("SUPPLIER SUBTOTAL", request.totals.subtotal),
            amount("VAT", request.totals.vat),
            PdfRow.total(
                PdfCell.text("PURCHASE ORDER TOTAL", PdfTextType.HEADING),
                PdfCell.right(request.totals.total, PdfTextType.HEADING)
            )
        )

    private fun amount(
        label: String,
        value: String
    ): PdfRow =
        PdfRow.normal(
            PdfCell.label(label),
            PdfCell.right(value)
        )

    private fun partyCell(
        label: String,
        party: PurchaseOrderParty
    ): PdfCell =
        PdfCell.party(
            label = label,
            name = party.name,
            lines = party.lines
        )

    private fun DeFerrantiOrderDocuments.toPurchaseOrderRequest(): PurchaseOrderPdfRequest =
        PurchaseOrderPdfRequest(
            logoPath = branding.logoPath,
            subtitle = branding.subtitle,
            footerText = branding.footerText,
            purchaseOrderNumber = references.purchaseOrderNumber,
            purchaseOrderDate = references.purchaseOrderDate,
            customerOrderNumber = references.customerOrderNumber,
            requiredDespatchDate = references.requiredDespatchDate,
            purchaser = parties.purchaser.toPurchaseOrderParty(),
            supplier =
                PurchaseOrderParty(
                    name = parties.supplier.name,
                    lines =
                        parties.supplier.lines(includeContact = false) +
                                goods.mapNotNull {
                                    it.supplierProductId?.let { supplierProductId ->
                                        "Supplier product ID: $supplierProductId"
                                    }
                                }.distinct()
                ),
            delivery = parties.deliverTo.toPurchaseOrderParty(includeCustomerType = false),
            lines = goods.map { it.toPurchaseOrderLine() },
            totals =
                PurchaseOrderTotals(
                    subtotal = totals.supplierSubtotal,
                    vat = totals.vatTreatment,
                    total = totals.purchaseOrderTotal
                ),
            packingLines =
                listOf(
                    "Prepare ${packing.contents} on ${packing.packageCount.lowercase()} ${packing.packageType.lowercase()}.",
                    "Expected packed dimensions: ${packing.dimensions}.",
                    "Expected gross packed weight: ${packing.grossWeight}."
                ) +
                        listOfNotNull(
                            packing.packingCost?.let {
                                "Packaging cost stored for checkout: $it."
                            }
                        ) +
                        listOf("Do not invoice packaging twice."),
            directShipmentInstructionLines = listOf(
                "Ship from ${parties.supplier.name} directly to the checkout consignee.",
                "Incoterm: ${shipment.incoterm}.",
                "${parties.importerOfRecord.name} is importer of record.",
                "No import charges are to be collected from the consignee."
            ),
            requiredDocumentLines = notes.requiredDocumentLines,
            supplierConfirmationLines = checks.supplierConfirmationLines,
            authorisationLines = checks.authorisationLines,
            controlNote = notes.purchaseOrderControlNote
        )

    private fun DocumentParty.toPurchaseOrderParty(
        includeCustomerType: Boolean = false
    ): PurchaseOrderParty =
        PurchaseOrderParty(
            name = name,
            lines = lines(includeCustomerType = includeCustomerType)
        )

    private fun DocumentGoodsLine.toPurchaseOrderLine(): PurchaseOrderLine =
        PurchaseOrderLine(
            productName = productName,
            specificationLines = specificationLines,
            area = area,
            boxes = boxes,
            tiles = tiles,
            unitPrice = unitPrice ?: "",
            lineTotal = lineTotal ?: "",
            unitPriceLabel = unitPriceLabel,
            deFerrantiSku = deFerrantiSku,
            supplierProductId = supplierProductId
        )
}
