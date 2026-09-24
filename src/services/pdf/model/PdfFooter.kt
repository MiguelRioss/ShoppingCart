package services.pdf.model

/**
 * Footer repeated at the bottom of every generated page.
 *
 * @property leftText organisation or document text shown on the left
 * @property showPageNumber whether the current page number is shown on the right
 */
data class PdfFooter(
    val leftText: String,
    val showPageNumber: Boolean = true
)
