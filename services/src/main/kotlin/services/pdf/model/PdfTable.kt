package services.pdf.model

/**
 * A table block within a PDF document.
 *
 * @property rows rows rendered from top to bottom
 * @property columnWidths optional relative widths; when absent, the renderer uses equal widths
 */
data class PdfTable(
    val rows: List<PdfRow>,
    val columnWidths: List<Float>? = null
) : PdfElement {

    companion object {

        /** Creates a table from explicit relative [widths] and vararg rows. */
        fun withWidths(
            widths: List<Float>,
            vararg rows: PdfRow
        ): PdfTable =
            PdfTable(
                columnWidths = widths,
                rows = rows.toList()
            )

        /** Creates a table from vararg relative widths and an existing row list. */
        fun withWidths(
            vararg widths: Float,
            rows: List<PdfRow>
        ): PdfTable =
            PdfTable(
                columnWidths = widths.toList(),
                rows = rows
            )

        /** Creates a single-row table whose cells all receive the same width. */
        fun equalColumns(
            vararg cells: PdfCell
        ): PdfTable =
            PdfTable(
                columnWidths =
                    List(
                        cells.size
                    ) {
                        1f
                    },
                rows = listOf(
                    PdfRow.normal(
                        *cells
                    )
                )
            )
    }
}
