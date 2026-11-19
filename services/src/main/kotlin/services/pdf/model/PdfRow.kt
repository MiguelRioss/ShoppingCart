package services.pdf.model

import pdf.model.PdfRowType

/**
 * One table row, composed of [cells] and styled according to [type].
 *
 * Cell column spans must collectively fit the number of columns inferred for the table.
 */
data class PdfRow(
    val cells: List<PdfCell>,
    val type: PdfRowType = PdfRowType.NORMAL
) {

    companion object {

        /** Creates an ordinary white table row. */
        fun normal(
            vararg cells: PdfCell
        ): PdfRow =
            PdfRow(
                cells = cells.toList()
            )

        /** Creates a table heading row whose cells use header presentation. */
        fun header(
            vararg cells: PdfCell
        ): PdfRow =
            PdfRow(
                type = PdfRowType.HEADER,
                cells = cells.toList()
            )

        /** Creates an emphasized row for totals and financial summaries. */
        fun total(
            vararg cells: PdfCell
        ): PdfRow =
            PdfRow(
                type = PdfRowType.TOTAL,
                cells = cells.toList()
            )
    }
}
