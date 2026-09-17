package services.pdf

import java.io.File
import services.pdf.model.OpenPdfGenerator
import services.pdf.model.PdfCell
import services.pdf.model.PdfDocument
import services.pdf.model.PdfFooter
import services.pdf.model.PdfHeader
import services.pdf.model.PdfRow
import services.pdf.model.PdfTable
import services.pdf.model.PdfText
import services.pdf.model.PdfTextType

class DeliveryNotePdf(
    private val generator: PdfGenerator = OpenPdfGenerator()
) {

    fun generate(documents: DeFerrantiOrderDocuments): ByteArray =
        generator.generate(createDocument(documents))

    fun generateToFile(
        documents: DeFerrantiOrderDocuments,
        outputPath: String
    ) {
        File(outputPath).writeBytes(generate(documents))
    }

    fun createDocument(documents: DeFerrantiOrderDocuments): PdfDocument =
        PdfDocument(
            header =
                PdfHeader(
                    logoPath = documents.branding.logoPath,
                    title = "DELIVERY NOTE",
                    subtitle = documents.branding.subtitle
                ),
            elements = listOf(
                references(documents),
                parties(documents),
                goods(documents),
                shipmentTerms(documents),
                PdfTable.equalColumns(
                    PdfCell.titled("DELIVERY CONDITION", documents.checks.deliveryConditionLines),
                    PdfCell.titled("EXCEPTIONS / DAMAGE", documents.checks.deliveryExceptionLines)
                ),
                PdfTable.equalColumns(
                    PdfCell.titled("RECEIVED BY", documents.checks.receivedByLines),
                    PdfCell.titled("DELIVERY DRIVER / COURIER", documents.checks.courierReceiptLines)
                ),
                PdfTable.equalColumns(
                    PdfCell.titled("IMPORTANT", documents.notes.deliveryImportantLines),
                    PdfCell.titled("DOCUMENT MATCH", documents.notes.documentMatchLines)
                ),
                PdfTable.withWidths(
                    listOf(1f),
                    PdfRow.normal(PdfCell.text(documents.notes.deliveryNoteControlNote, PdfTextType.SMALL))
                )
            ),
            footer = PdfFooter(documents.branding.footerText)
        )

    private fun references(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.withWidths(
            listOf(1f, 1f, 1f, 1f),
            PdfRow.total(
                PdfCell.label("DELIVERY NOTE"),
                PdfCell.label("DELIVERY DATE"),
                PdfCell.label("ORDER REFERENCE"),
                PdfCell.label("FEDEX TRACKING")
            ),
            PdfRow.normal(
                PdfCell.text(documents.references.deliveryNoteNumber),
                PdfCell.text(documents.references.deliveryDate),
                PdfCell.text(documents.references.customerOrderNumber),
                PdfCell.text(documents.shipment.trackingNumber)
            )
        )

    private fun parties(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.equalColumns(
            PdfCell.party(
                label = "SUPPLIER / SELLER",
                name = documents.parties.seller.name,
                lines = documents.parties.seller.lines(includeContact = false)
            ),
            PdfCell.party(
                label = "DESPATCHED FROM",
                name = documents.parties.shipFrom.name,
                lines = documents.parties.shipFrom.lines(includeContact = false)
            ),
            PdfCell.party(
                label = "DELIVER TO / RECIPIENT",
                name = documents.parties.deliverTo.name,
                lines = documents.parties.deliverTo.lines(includeCustomerType = true)
            )
        )

    private fun goods(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.withWidths(
            0.55f,
            4f,
            1f,
            0.9f,
            0.9f,
            1f,
            1f,
            rows = listOf(
                PdfRow.header(
                    PdfCell.header("LINE"),
                    PdfCell.header("PRODUCT / CONTENTS"),
                    PdfCell.header("AREA"),
                    PdfCell.header("BOXES"),
                    PdfCell.header("TILES"),
                    PdfCell.header("PACKAGE"),
                    PdfCell.header("GROSS WT.")
                )
            ) +
                    documents.goods.mapIndexed { index, line ->
                        PdfRow.normal(
                            PdfCell.centered((index + 1).toString()),
                            goodsCell(line),
                            PdfCell.centered(line.area),
                            PdfCell.centered(line.boxes),
                            PdfCell.centered(line.tiles),
                            PdfCell.centered(line.packageDescription.orEmpty()),
                            PdfCell.centered(line.grossWeight.orEmpty())
                        )
                    }
        )

    private fun shipmentTerms(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.withWidths(
            listOf(1f, 1.4f, 1.3f, 1.3f),
            PdfRow.total(
                PdfCell.label("PALLET DIMENSIONS"),
                PdfCell.label("DELIVERY TERMS"),
                PdfCell.label("COURIER"),
                PdfCell.label("AWB")
            ),
            PdfRow.normal(
                PdfCell.text(documents.packing.dimensions),
                PdfCell.text(documents.shipment.deliveryTerms),
                PdfCell.text("${documents.shipment.courier} - ${documents.shipment.service}"),
                PdfCell.text(documents.shipment.awb)
            )
        )

    private fun goodsCell(line: DocumentGoodsLine): PdfCell =
        PdfCell(
            content =
                listOf(PdfText(line.productName, PdfTextType.BOLD)) +
                        line.specificationLines.map(::PdfText) +
                        listOfNotNull(
                            line.deFerrantiSku?.let { PdfText("SKU: $it", PdfTextType.BOLD) },
                            line.supplierProductId?.let { PdfText("Manufacturer Product ID: $it", PdfTextType.BOLD) }
                        )
        )
}
