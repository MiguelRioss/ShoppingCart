package services.pdf.model

/**
 * Semantic text styles understood by the PDF renderer.
 *
 * Keeping styles semantic prevents document builders from depending on concrete fonts,
 * sizes, and colours. The active [services.pdf.PdfGenerator] maps each value to its visual
 * representation.
 */
enum class PdfTextType {
    LABEL,
    HEADING,
    NORMAL,
    BOLD,
    SMALL,
    TABLE_HEADER
}
