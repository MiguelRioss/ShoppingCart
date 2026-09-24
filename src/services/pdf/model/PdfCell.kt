package services.pdf.model

import pdf.model.PdfAlignment

/**
 * Describes one cell in a [PdfTable].
 *
 * @property content ordered text fragments rendered on separate lines
 * @property alignment horizontal alignment applied to all cell content
 * @property columnSpan number of table columns occupied by this cell
 * @property padding whitespace, in PDF points, around the content
 * @property leading vertical distance, in points, between text baselines
 * @property minimumHeight optional minimum cell height in PDF points
 */
data class PdfCell(
    val content: List<PdfText>,
    val alignment: PdfAlignment = PdfAlignment.LEFT,
    val columnSpan: Int = 1,
    val padding: Float = 9f,
    val leading: Float = 13f,
    val minimumHeight: Float? = null
) {

    companion object {

        /** Creates a left-aligned cell containing one text fragment. */
        fun text(
            value: String,
            type: PdfTextType = PdfTextType.NORMAL
        ): PdfCell =
            PdfCell(
                content = listOf(
                    PdfText(
                        value,
                        type
                    )
                )
            )

        /** Creates a cell containing a small semantic label. */
        fun label(
            value: String
        ): PdfCell =
            text(
                value,
                PdfTextType.LABEL
            )

        /** Creates a compact centred cell intended for a dark table header row. */
        fun header(
            value: String
        ): PdfCell =
            PdfCell(
                alignment = PdfAlignment.CENTER,
                padding = 5f,
                leading = 9f,
                content = listOf(
                    PdfText(
                        value,
                        PdfTextType.TABLE_HEADER
                    )
                )
            )

        /** Creates a centred cell containing one line of normal text. */
        fun centered(
            value: String
        ): PdfCell =
            PdfCell(
                alignment = PdfAlignment.CENTER,
                content = listOf(
                    PdfText(
                        value
                    )
                )
            )

        /** Creates a centred cell containing each supplied value on a separate line. */
        fun centeredLines(
            vararg values: String
        ): PdfCell =
            PdfCell(
                alignment = PdfAlignment.CENTER,
                content =
                    values.map {
                        PdfText(
                            it
                        )
                    }
            )

        /** Creates a right-aligned cell, commonly used for quantities and monetary values. */
        fun right(
            value: String,
            type: PdfTextType = PdfTextType.NORMAL
        ): PdfCell =
            PdfCell(
                alignment = PdfAlignment.RIGHT,
                content = listOf(
                    PdfText(
                        value,
                        type
                    )
                )
            )

        /** Creates a labelled cell followed by zero or more body lines. */
        fun titled(
            title: String,
            lines: List<String>
        ): PdfCell =
            PdfCell(
                content =
                    listOf(
                        PdfText(
                            title,
                            PdfTextType.LABEL
                        )
                    ) +
                            lines.texts()
            )

        /** Creates the taller address block used for purchaser and supplier parties. */
        fun party(
            label: String,
            name: String,
            lines: List<String>
        ): PdfCell =
            PdfCell(
                padding = 12f,
                leading = 14.5f,
                minimumHeight = 95f,
                content =
                    listOf(
                        PdfText(
                            label,
                            PdfTextType.LABEL
                        ),
                        PdfText(
                            name,
                            PdfTextType.HEADING
                        )
                    ) +
                            lines.texts()
            )

        private fun List<String>.texts(): List<PdfText> =
            filter(String::isNotBlank)
                .map(::PdfText)
    }
}
