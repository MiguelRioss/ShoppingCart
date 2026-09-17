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

class CommercialInvoicePdf(
    private val generator: PdfGenerator = OpenPdfGenerator()
) {

    fun generate(
        documents: DeFerrantiOrderDocuments
    ): ByteArray =
        generator.generate(
            createDocument(documents)
        )

    fun generateToFile(
        documents: DeFerrantiOrderDocuments,
        outputPath: String
    ) {
        File(outputPath)
            .writeBytes(
                generate(documents)
            )
    }

    fun createDocument(
        documents: DeFerrantiOrderDocuments
    ): PdfDocument =
        PdfDocument(

            header =
                PdfHeader(
                    logoPath =
                        documents.branding.logoPath,

                    title =
                        "COMMERCIAL INVOICE",

                    subtitle =
                        documents.branding.subtitle
                ),

            elements =
                listOf(

                    invoiceReferences(documents),

                    topParties(documents),

                    customerParties(documents),

                    goods(documents),

                    invoiceSummary(documents),

                    PdfTable.equalColumns(

                        PdfCell.titled(
                            "ORIGIN AND DECLARATION",
                            originDeclaration(documents)
                        ),

                        PdfCell.titled(
                            "EXPORTER SIGNATURE",
                            exporterSignature(documents)
                        )
                    ),

                    PdfTable.withWidths(
                        listOf(1f),

                        PdfRow.normal(
                            PdfCell.text(
                                invoiceControlNote(documents),
                                PdfTextType.SMALL
                            )
                        )
                    )
                ),

            footer =
                PdfFooter(
                    documents.branding.footerText
                )
        )

    // =============================================================
    // INVOICE REFERENCES
    // =============================================================

    private fun invoiceReferences(
        documents: DeFerrantiOrderDocuments
    ): PdfTable =
        PdfTable.withWidths(

            listOf(
                1f,
                1f,
                1f,
                1f
            ),

            PdfRow.total(
                PdfCell.label(
                    "INVOICE NUMBER"
                ),

                PdfCell.label(
                    "INVOICE DATE"
                ),

                PdfCell.label(
                    "ORDER REFERENCE"
                ),

                PdfCell.label(
                    "FEDEX AWB"
                )
            ),

            PdfRow.normal(

                PdfCell.text(
                    documents.references.invoiceNumber
                        ?: "INVOICE NUMBER REQUIRED"
                ),

                /*
                 * At the moment you do not have a separate
                 * invoice date in DocumentReferences.
                 *
                 * For the test we use the purchase order date.
                 */
                PdfCell.text(
                    documents.references.purchaseOrderDate
                ),

                PdfCell.text(
                    documents.references.customerOrderNumber
                ),

                PdfCell.text(
                    documents.shipment.awb
                )
            )
        )

    // =============================================================
    // SELLER / COLLECTION / EXPORTER
    // =============================================================

    private fun topParties(
        documents: DeFerrantiOrderDocuments
    ): PdfTable =
        PdfTable.equalColumns(

            partyCell(
                label =
                    "1. SELLER AND INVOICE ISSUER",

                party =
                    documents.parties.seller
            ),

            partyCell(
                label =
                    "2. SHIP FROM / COLLECTION POINT",

                party =
                    documents.parties.shipFrom
            ),

            exporterCell(documents)
        )

    private fun exporterCell(
        documents: DeFerrantiOrderDocuments
    ): PdfCell {

        val exporter =
            documents.parties.supplier

        return PdfCell(

            content =
                listOf(
                    PdfText(
                        "3. EU CUSTOMS EXPORTER",
                        PdfTextType.HEADING
                    ),

                    PdfText(
                        exporter.name,
                        PdfTextType.BOLD
                    )
                ) +

                        exporter
                            .lines(
                                includeContact = false
                            )
                            .map(::PdfText) +

                        listOf(
                            PdfText(
                                "Acting as EU-established exporter for this shipment."
                            ),

                            PdfText(
                                "Exporter's relationship: fulfilment / dispatch party for De Ferranti Limited."
                            ),

                            PdfText(
                                "Exporter authorisation or representation records to be retained."
                            )
                        )
        )
    }

    // =============================================================
    // BUYER / CONSIGNEE / IMPORTER
    // =============================================================

    private fun customerParties(
        documents: DeFerrantiOrderDocuments
    ): PdfTable =
        PdfTable.equalColumns(

            /*
             * TEMPORARY:
             *
             * Your existing DocumentParties model has no separate
             * billTo party yet.
             *
             * For this test, deliverTo is used for both
             * buyer and consignee.
             */
            PdfCell.party(
                label =
                    "4. BILL TO / BUYER",

                name =
                    documents.parties.deliverTo.name,

                lines =
                    documents.parties.deliverTo.lines(
                        includeCustomerType = true
                    )
            ),

            PdfCell.party(
                label =
                    "5. SHIP TO / CONSIGNEE",

                name =
                    documents.parties.deliverTo.name,

                lines =
                    documents.parties.deliverTo.lines(
                        includeCustomerType = false
                    )
            ),

            importerCell(documents)
        )

    private fun importerCell(
        documents: DeFerrantiOrderDocuments
    ): PdfCell {

        val importer =
            documents.parties.importerOfRecord

        return PdfCell(

            content =
                listOf(
                    PdfText(
                        "6. IMPORTER OF RECORD",
                        PdfTextType.HEADING
                    ),

                    PdfText(
                        importer.name,
                        PdfTextType.BOLD
                    )
                ) +

                        importer
                            .lines(
                                includeContact = false
                            )
                            .map(::PdfText) +

                        listOf(
                            PdfText(
                                "Duties and taxes billed to De Ferranti."
                            )
                        )
        )
    }

    // =============================================================
    // GOODS
    // =============================================================

    private fun goods(
        documents: DeFerrantiOrderDocuments
    ): PdfTable =
        PdfTable.withWidths(

            0.5f,
            4.1f,
            1f,
            0.9f,
            1f,
            1f,
            1.2f,
            1.3f,

            rows =
                listOf(

                    PdfRow.header(

                        PdfCell.header(
                            "LINE"
                        ),

                        PdfCell.header(
                            "DETAILED GOODS DESCRIPTION"
                        ),

                        PdfCell.header(
                            "HS CODE"
                        ),

                        PdfCell.header(
                            "ORIGIN"
                        ),

                        PdfCell.header(
                            "QTY"
                        ),

                        PdfCell.header(
                            "NET WT."
                        ),

                        PdfCell.header(
                            "UNIT VALUE"
                        ),

                        PdfCell.header(
                            "LINE VALUE"
                        )
                    )
                ) +

                        documents.goods
                            .mapIndexed { index, line ->

                                PdfRow.normal(

                                    PdfCell.centered(
                                        (index + 1)
                                            .toString()
                                    ),

                                    goodsDescriptionCell(
                                        line
                                    ),

                                    PdfCell.centered(
                                        line.hsCode
                                            .orEmpty()
                                    ),

                                    PdfCell.centered(
                                        line.originCountry
                                            .orEmpty()
                                    ),

                                    quantityCell(
                                        line
                                    ),

                                    PdfCell.centered(
                                        line.netWeight
                                            ?: "NET WEIGHT REQUIRED"
                                    ),

                                    PdfCell.centeredLines(
                                        line.unitPrice
                                            ?: "VALUE REQUIRED",

                                        line.unitPriceLabel
                                    ),

                                    PdfCell.centered(
                                        line.lineTotal
                                            ?: "VALUE REQUIRED"
                                    )
                                )
                            }
        )

    private fun goodsDescriptionCell(
        line: DocumentGoodsLine
    ): PdfCell =
        PdfCell(

            content =

                listOf(

                    PdfText(
                        line.productName,
                        PdfTextType.BOLD
                    )

                ) +

                        line.specificationLines
                            .filter(
                                String::isNotBlank
                            )
                            .map(
                                ::PdfText
                            ) +

                        listOfNotNull(

                            line.deFerrantiSku
                                ?.let {
                                    PdfText(
                                        "Merchant product ID: $it",
                                        PdfTextType.BOLD
                                    )
                                },

                            line.supplierProductId
                                ?.let {
                                    PdfText(
                                        "Manufacturer product ID: $it",
                                        PdfTextType.BOLD
                                    )
                                },

                            line.standardisedProductId
                                ?.let {
                                    PdfText(
                                        "Standardised product ID: $it"
                                    )
                                }
                        )
        )

    private fun quantityCell(
        line: DocumentGoodsLine
    ): PdfCell =
        PdfCell.centeredLines(
            line.area,
            line.boxes,
            line.tiles
        )

    // =============================================================
    // COMMERCIAL / CUSTOMS SUMMARY
    // =============================================================

    private fun invoiceSummary(
        documents: DeFerrantiOrderDocuments
    ): PdfTable =
        PdfTable.withWidths(

            listOf(
                1.2f,
                3.7f,
                1.1f,
                1.4f
            ),

            summaryRow(
                label =
                    "REASON FOR EXPORT",

                value =
                    documents.shipment.purpose,

                amountLabel =
                    "GOODS VALUE",

                amount =
                    documents.totals.purchaseOrderTotal
            ),

            summaryRow(
                label =
                    "INCOTERM",

                value =
                    documents.shipment.deliveryTerms,

                amountLabel =
                    "PACKAGING",

                amount =
                    documents.packing.packingCost
                        ?: "EUR 0.00"
            ),

            summaryRow(
                label =
                    "DUTIES / TAXES",

                value =
                    "Payable by De Ferranti Limited. " +
                            "No import charges are to be collected from consignee.",

                amountLabel =
                    "FEDEX / DDP",

                amount =
                    "FEDEX API"
            ),

            summaryRow(
                label =
                    "PACKAGES",

                value =
                    packageDescription(
                        documents
                    ),

                amountLabel =
                    "VAT",

                amount =
                    documents.totals.vatTreatment
            ),

            summaryRow(
                label =
                    "WEIGHT",

                value =
                    weightDescription(
                        documents
                    ),

                amountLabel =
                    "TOTAL INVOICE",

                /*
                 * At present your shared model has no separate
                 * final checkout invoice total.
                 *
                 * For this test we use the existing total.
                 */
                amount =
                    documents.totals.purchaseOrderTotal
            ),

            summaryRow(
                label =
                    "CURRENCY",

                value =
                    "EUR",

                amountLabel =
                    "CUSTOMS GOODS VALUE",

                amount =
                    documents.totals.purchaseOrderTotal
            ),

            PdfRow.normal(

                PdfCell.label(
                    "PAYMENT TERMS"
                ),

                PdfCell.text(
                    "TEST - CHECKOUT PAYMENT STATUS REQUIRED"
                ),

                PdfCell.text(""),

                PdfCell.text("")
            )
        )

    private fun summaryRow(
        label: String,
        value: String,
        amountLabel: String,
        amount: String
    ): PdfRow =
        PdfRow.normal(

            PdfCell.label(
                label
            ),

            PdfCell.text(
                value
            ),

            PdfCell.label(
                amountLabel
            ),

            PdfCell.right(
                amount
            )
        )

    // =============================================================
    // DECLARATION
    // =============================================================

    private fun originDeclaration(
        documents: DeFerrantiOrderDocuments
    ): List<String> {

        val origin =
            documents.goods
                .firstOrNull()
                ?.originCountry
                ?: "PRODUCT ORIGIN REQUIRED"

        return listOf(

            "Country of non-preferential origin: $origin.",

            "No preferential origin claim is made in this test document.",

            "If preferential treatment is claimed, the exporter must provide the required origin declaration and retain supporting evidence."
        )
    }

    private fun exporterSignature(
        documents: DeFerrantiOrderDocuments
    ): List<String> =
        listOf(

            "Name: __________________________",

            "Position: _______________________",

            "For ${documents.parties.supplier.name}",

            "Date: ___________________________"
        )

    // =============================================================
    // HELPERS
    // =============================================================

    private fun partyCell(
        label: String,
        party: DocumentParty
    ): PdfCell =
        PdfCell.party(

            label =
                label,

            name =
                party.name,

            lines =
                party.lines()
        )

    private fun packageDescription(
        documents: DeFerrantiOrderDocuments
    ): String =
        "${documents.packing.packageCount} " +
                "${documents.packing.packageType}, " +
                "${documents.packing.dimensions}; " +
                documents.goods.joinToString(
                    separator = ", "
                ) {
                    it.boxes
                }

    private fun weightDescription(
        documents: DeFerrantiOrderDocuments
    ): String {

        val net =
            documents.goods
                .firstOrNull()
                ?.netWeight
                ?: "NET WEIGHT REQUIRED"

        return "Net $net | Gross ${documents.packing.grossWeight}"
    }

    private fun invoiceControlNote(
        documents: DeFerrantiOrderDocuments
    ): String {

        val product =
            documents.goods
                .firstOrNull()

        return buildString {

            append(
                "TEST DOCUMENT. "
            )

            append(
                "Commercial invoice generated from checkout/product data. "
            )

            append(
                "Customer, tax and FedEx placeholders must be resolved before customs use."
            )

            if (product != null) {

                append(
                    " Product: ${product.productName};"
                )

                append(
                    " ${product.area};"
                )

                append(
                    " ${product.boxes};"
                )

                append(
                    " ${product.tiles};"
                )

                append(
                    " gross weight ${documents.packing.grossWeight};"
                )

                append(
                    " goods value ${documents.totals.purchaseOrderTotal}."
                )
            }
        }
    }
}