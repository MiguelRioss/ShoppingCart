package services.pdf.model

data class PdfText(
    val text: String,
    val type: PdfTextType = PdfTextType.NORMAL
)