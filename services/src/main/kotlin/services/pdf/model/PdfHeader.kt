package services.pdf.model

/**
 * Header shown at the beginning of a generated PDF.
 *
 * @property logoPath filesystem path to the logo; a missing file produces an empty logo cell
 * @property title right-aligned document title
 * @property subtitle optional notice displayed beneath the title
 */
data class PdfHeader(
    val logoPath: String,
    val title: String,
    val subtitle: String? = null
)
