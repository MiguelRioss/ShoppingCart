package services.pdf.model

data class PdfFooter(
    val leftText: String,
    val showPageNumber: Boolean = true
)