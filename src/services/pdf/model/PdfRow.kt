package services.pdf.model

import pdf.model.PdfRowType

data class PdfRow(
    val cells: List<PdfCell>,
    val type: PdfRowType = PdfRowType.NORMAL
) {

    companion object {

        fun normal(
            vararg cells: PdfCell
        ): PdfRow =
            PdfRow(
                cells = cells.toList()
            )

        fun header(
            vararg cells: PdfCell
        ): PdfRow =
            PdfRow(
                type = PdfRowType.HEADER,
                cells = cells.toList()
            )

        fun total(
            vararg cells: PdfCell
        ): PdfRow =
            PdfRow(
                type = PdfRowType.TOTAL,
                cells = cells.toList()
            )
    }
}
