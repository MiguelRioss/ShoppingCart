package services.pdf.model

data class PdfTable(
    val rows: List<PdfRow>,
    val columnWidths: List<Float>? = null
) : PdfElement {

    companion object {

        fun withWidths(
            widths: List<Float>,
            vararg rows: PdfRow
        ): PdfTable =
            PdfTable(
                columnWidths = widths,
                rows = rows.toList()
            )

        fun withWidths(
            vararg widths: Float,
            rows: List<PdfRow>
        ): PdfTable =
            PdfTable(
                columnWidths = widths.toList(),
                rows = rows
            )

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
