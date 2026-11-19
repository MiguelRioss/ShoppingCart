package services.pdf.model

/**
 * A text fragment and its semantic [type] inside a PDF cell.
 *
 * A cell can combine several fragments so labels, headings, and normal text can be rendered
 * together without embedding presentation rules in document-building code.
 */
data class PdfText(
    val text: String,
    val type: PdfTextType = PdfTextType.NORMAL
)
