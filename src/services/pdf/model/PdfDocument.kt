package services.pdf.model

data class PdfDocument(
    val header: PdfHeader,
    val elements: List<PdfElement>,
    val footer: PdfFooter
)