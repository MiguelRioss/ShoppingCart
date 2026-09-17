package services.pdf

import services.pdf.model.PdfDocument

interface PdfGenerator {

    fun generate(
        document: PdfDocument
    ): ByteArray
}