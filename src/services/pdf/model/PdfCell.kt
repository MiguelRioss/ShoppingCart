package services.pdf.model

import pdf.model.PdfAlignment

data class PdfCell(
    val content: List<PdfText>,
    val alignment: PdfAlignment = PdfAlignment.LEFT,
    val columnSpan: Int = 1,
    val padding: Float = 9f,
    val leading: Float = 13f,
    val minimumHeight: Float? = null
) {

    companion object {

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

        fun label(
            value: String
        ): PdfCell =
            text(
                value,
                PdfTextType.LABEL
            )

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
