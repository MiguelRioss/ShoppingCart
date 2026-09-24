package services.pdf.model

/**
 * Complete, renderer-independent representation of a PDF document.
 *
 * The model intentionally contains content and semantic presentation only. Concrete PDF
 * library objects are introduced later by [services.pdf.PdfGenerator].
 *
 * @property header document identity rendered before the body
 * @property elements ordered body blocks
 * @property footer content repeated on every page
 */
data class PdfDocument(
    val header: PdfHeader,
    val elements: List<PdfElement>,
    val footer: PdfFooter
)
