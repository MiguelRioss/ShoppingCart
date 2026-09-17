package services.pdf.model

data class PdfHeader(
    val logoPath: String,
    val title: String,
    val subtitle: String? = null
)