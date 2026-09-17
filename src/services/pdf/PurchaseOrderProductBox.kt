package services.pdf

import services.pdf.model.PdfCell
import services.pdf.model.PdfRow
import services.pdf.model.PdfText
import services.pdf.model.PdfTextType

object PurchaseOrderProductBox {

    fun row(
        lineNumber: Int,
        line: PurchaseOrderLine
    ): PdfRow =
        PdfRow.normal(
            PdfCell.centered(lineNumber.toString()),
            PdfCell(
                content =
                    listOf(
                        PdfText(
                            line.productName,
                            PdfTextType.BOLD
                        )
                    ) +
                            line.specificationLines.texts() +
                            listOfNotNull(
                                line.deFerrantiSku?.let {
                                    PdfText(
                                        "De Ferranti SKU: $it",
                                        PdfTextType.BOLD
                                    )
                                },
                                line.supplierProductId?.let {
                                    PdfText(
                                        "Supplier product ID: $it",
                                        PdfTextType.BOLD
                                    )
                                }
                            )
            ),
            PdfCell.centered(line.area),
            PdfCell.centered(line.boxes),
            PdfCell.centered(line.tiles),
            PdfCell.centeredLines(
                line.unitPrice,
                line.unitPriceLabel
            ),
            PdfCell.centered(line.lineTotal)
        )

    private fun List<String>.texts(): List<PdfText> =
        filter(String::isNotBlank)
            .map(::PdfText)
}
