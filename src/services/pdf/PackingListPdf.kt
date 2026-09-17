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

class PackingListPdf(
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
                    title = "PACKING LIST",
                    subtitle = documents.branding.subtitle
                ),
            elements = listOf(
                references(documents),
                parties(documents),
                goods(documents),
                packageDetails(documents),
                PdfTable.equalColumns(
                    PdfCell.titled("SHIPMENT DETAILS", shipmentLines(documents)),
                    PdfCell.titled("HANDLING", documents.packing.handlingLines),
                    PdfCell.titled("B2C / B2B", documents.notes.b2bB2cLines)
                ),
                PdfTable.equalColumns(
                    PdfCell.titled("PACKED BY", documents.checks.packedByLines),
                    PdfCell.titled("FINAL CHECK", documents.checks.finalCheckLines)
                ),
                PdfTable.withWidths(
                    listOf(1f),
                    PdfRow.normal(PdfCell.text(documents.notes.packingListControlNote, PdfTextType.SMALL))
                )
            ),
            footer = PdfFooter(documents.branding.footerText)
        )

    private fun references(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.withWidths(
            listOf(1f, 1f, 1f, 1f),
            PdfRow.total(
                PdfCell.label("PACKING LIST"),
                PdfCell.label("PACKING DATE"),
                PdfCell.label("ORDER REFERENCE"),
                PdfCell.label("FEDEX AWB")
            ),
            PdfRow.normal(
                PdfCell.text(documents.references.packingListNumber),
                PdfCell.text(documents.references.packingDate),
                PdfCell.text(documents.references.customerOrderNumber),
                PdfCell.text(documents.shipment.awb)
            )
        )

    private fun parties(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.equalColumns(
            PdfCell.party(
                label = "SHIP FROM",
                name = documents.parties.shipFrom.name,
                lines = documents.parties.shipFrom.lines(includeContact = false)
            ),
            PdfCell.party(
                label = "SHIP TO / CONSIGNEE",
                name = documents.parties.deliverTo.name,
                lines = documents.parties.deliverTo.lines(includeCustomerType = true)
            ),
            PdfCell.party(
                label = "IMPORTER OF RECORD",
                name = documents.parties.importerOfRecord.name,
                lines = documents.parties.importerOfRecord.lines(includeContact = false)
            )
        )

    private fun goods(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.withWidths(
            0.55f,
            3.3f,
            1f,
            1f,
            0.9f,
            0.85f,
            0.85f,
            0.9f,
            1f,
            rows = listOf(
                PdfRow.header(
                    PdfCell.header("LINE"),
                    PdfCell.header("CONTENTS"),
                    PdfCell.header("HS CODE"),
                    PdfCell.header("ORIGIN"),
                    PdfCell.header("AREA"),
                    PdfCell.header("BOXES"),
                    PdfCell.header("TILES"),
                    PdfCell.header("NET WT."),
                    PdfCell.header("GROSS WT.")
                )
            ) +
                    documents.goods.mapIndexed { index, line ->
                        PdfRow.normal(
                            PdfCell.centered((index + 1).toString()),
                            goodsCell(line),
                            PdfCell.centered(line.hsCode.orEmpty()),
                            PdfCell.centered(line.originCountry.orEmpty()),
                            PdfCell.centered(line.area),
                            PdfCell.centered(line.boxes),
                            PdfCell.centered(line.tiles),
                            PdfCell.centered(line.netWeight.orEmpty()),
                            PdfCell.centered(line.grossWeight.orEmpty())
                        )
                    }
        )

    private fun packageDetails(documents: DeFerrantiOrderDocuments): PdfTable =
        PdfTable.withWidths(
            listOf(1f, 1.2f, 1f, 2.5f),
            PdfRow.header(
                PdfCell.header("PACKAGE TYPE"),
                PdfCell.header("DIMENSIONS"),
                PdfCell.header("GROSS WEIGHT"),
                PdfCell.header("CONTENTS")
            ),
            PdfRow.normal(
                PdfCell.text("${documents.packing.packageCount} ${documents.packing.packageType}"),
                PdfCell.text(documents.packing.dimensions),
                PdfCell.text(documents.packing.grossWeight),
                PdfCell.text(documents.packing.contents)
            )
        )

    private fun goodsCell(line: DocumentGoodsLine): PdfCell =
        PdfCell(
            content =
                listOf(PdfText(line.productName, PdfTextType.BOLD)) +
                        line.specificationLines.map(::PdfText) +
                        listOfNotNull(
                            line.deFerrantiSku?.let { PdfText("SKU / Merchant Product ID: $it", PdfTextType.BOLD) },
                            line.supplierProductId?.let { PdfText("Manufacturer Product ID: $it", PdfTextType.BOLD) },
                            line.standardisedProductId?.let { PdfText("Standardised Product ID: $it") }
                        )
        )

    private fun shipmentLines(documents: DeFerrantiOrderDocuments): List<String> =
        listOf(
            "Shipment purpose: ${documents.shipment.purpose}",
            "Incoterm: ${documents.shipment.incoterm}",
            "Courier: ${documents.shipment.courier}",
            "Service: ${documents.shipment.service}",
            "Tracking number: ${documents.shipment.trackingNumber}"
        )
}
