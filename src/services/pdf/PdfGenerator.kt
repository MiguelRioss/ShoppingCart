package services.pdf

import services.pdf.model.PdfDocument

/**
 * Converts the application's library-independent [PdfDocument] model into a PDF file.
 *
 * Implementations own the details of the underlying PDF library. Callers therefore build
 * documents with the types in `services.pdf.model` and receive the completed file as bytes
 * without depending on OpenPDF or filesystem access.
 */
interface PdfGenerator {

    /**
     * Renders [document] and returns the complete PDF binary content.
     *
     * The returned array can be written to disk, attached to an email, or returned from an
     * HTTP endpoint. Rendering failures are propagated from the concrete implementation.
     */
    fun generate(
        document: PdfDocument
    ): ByteArray
}
