package services.pdf.model

import java.io.ByteArrayOutputStream
import java.io.File
import org.openpdf.text.Chunk
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.Image
import org.openpdf.text.PageSize
import org.openpdf.text.Phrase
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPageEventHelper
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import org.openpdf.text.pdf.RGBColor
import pdf.model.PdfAlignment
import pdf.model.PdfRowType
import services.pdf.PdfGenerator

/**
 * [PdfGenerator] implementation backed by the OpenPDF library.
 *
 * It translates the neutral PDF model into an A4 document, renders semantic text and row
 * styles, repeats the configured footer on every page, and returns the result entirely in
 * memory. No files are created by this class.
 */
class OpenPdfGenerator : PdfGenerator {

    /** Renders [document] as an A4 PDF and returns its binary content. */
    override fun generate(
        document: PdfDocument
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        val pdfDocument =
            Document(
                PageSize.A4,
                18f,
                18f,
                30f,
                52f
            )

        val writer =
            PdfWriter.getInstance(
            pdfDocument,
            output
        )

        writer.pageEvent =
            FooterPageEvent(
                footer =
                    document.footer
            )

        pdfDocument.open()

        renderHeader(
            document = pdfDocument,
            header = document.header
        )

        document.elements.forEach { element ->

            when (element) {

                is PdfTable ->
                    renderTable(
                        document = pdfDocument,
                        table = element
                    )
            }
        }

        pdfDocument.close()

        return output.toByteArray()
    }

    private fun renderHeader(
        document: Document,
        header: PdfHeader
    ) {

        val table =
            PdfPTable(2)

        table.widthPercentage =
            100f

        val logoFile =
            File(header.logoPath)

        val logoCell =
            if (logoFile.exists()) {
                val logo =
                    Image.getInstance(
                        header.logoPath
                    )

                logo.scaleToFit(
                    220f,
                    80f
                )

                PdfPCell(logo)
            } else {
                System.err.println(
                    "PDF logo not found: ${logoFile.absolutePath}"
                )

                PdfPCell(
                    Phrase("")
                )
            }

        logoCell.border =
            PdfPCell.NO_BORDER

        logoCell.verticalAlignment =
            Element.ALIGN_TOP

        /*
         * Document title
         */
        val titleFont =
            FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                16f,
                RGBColor(
                    0,
                    0,
                    0
                )
            )

        val titleCell =
            PdfPCell(
                Phrase(
                    header.title,
                    titleFont
                )
            )

        titleCell.border =
            PdfPCell.NO_BORDER

        titleCell.horizontalAlignment =
            Element.ALIGN_RIGHT

        titleCell.verticalAlignment =
            Element.ALIGN_TOP

        table.addCell(
            logoCell
        )

        table.addCell(
            titleCell
        )

        document.add(
            table
        )

        /*
         * Optional subtitle underneath the header.
         *
         * Example:
         *
         * TEMPLATE EXAMPLE - NOT VALID FOR LIVE USE
         */
        header.subtitle?.let { subtitle ->

            val subtitleFont =
                FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    8f,
                    RGBColor(
                        170,
                        50,
                        40
                    )
                )

            val subtitleTable =
                PdfPTable(1)

            subtitleTable.widthPercentage =
                100f

            val subtitleCell =
                PdfPCell(
                    Phrase(
                        subtitle,
                        subtitleFont
                    )
                )

            subtitleCell.border =
                PdfPCell.NO_BORDER

            subtitleCell.horizontalAlignment =
                Element.ALIGN_RIGHT

            subtitleCell.paddingTop =
                8f

            subtitleCell.paddingBottom =
                6f

            subtitleTable.addCell(
                subtitleCell
            )

            document.add(
                subtitleTable
            )
        }
    }

    private fun renderTable(
        document: Document,
        table: PdfTable
    ) {

        if (table.rows.isEmpty()) {
            return
        }

        /*
         * Work out how many columns the table requires.
         *
         * columnSpan is taken into account.
         */
        val columnCount =
            table.rows.maxOf { row ->

                row.cells.sumOf { cell ->
                    cell.columnSpan
                }
            }

        val pdfTable =
            PdfPTable(
                columnCount
            )

        pdfTable.widthPercentage =
            100f

        pdfTable.setSpacingAfter(9f)

        /*
         * Custom column widths are optional.
         */
        table.columnWidths?.let { widths ->

            pdfTable.setWidths(
                widths.toFloatArray()
            )
        }

        /*
         * Render every row and cell.
         */
        table.rows.forEach { row ->

            row.cells.forEach { cell ->

                pdfTable.addCell(
                    createCell(
                        cell = cell,
                        row = row
                    )
                )
            }
        }

        document.add(
            pdfTable
        )
    }

    private fun createCell(
        cell: PdfCell,
        row: PdfRow
    ): PdfPCell {

        val phrase =
            Phrase(
                cell.leading
            )

        /*
         * A cell can contain multiple different
         * text elements.
         *
         * Example:
         *
         * PURCHASER
         * De Ferranti Limited
         * South Park Studios
         * 88 Peterborough Road
         */
        cell.content.forEach { text ->

            phrase.add(
                Chunk(
                    text.text + "\n",
                    fontFor(
                        text
                    )
                )
            )
        }

        val pdfCell =
            PdfPCell(
                phrase
            )

        /*
         * Allow a cell to occupy multiple columns.
         */
        pdfCell.colspan =
            cell.columnSpan

        /*
         * General cell spacing.
         */
        pdfCell.setPadding(
            cell.padding
        )

        pdfCell.paddingTop =
            cell.padding

        pdfCell.paddingBottom =
            cell.padding

        cell.minimumHeight?.let { minimumHeight ->
            pdfCell.minimumHeight =
                minimumHeight
        }

        pdfCell.setUseAscender(true)

        pdfCell.setUseDescender(true)

        pdfCell.verticalAlignment =
            Element.ALIGN_TOP

        /*
         * Horizontal text alignment.
         */
        pdfCell.horizontalAlignment =
            when (cell.alignment) {

                PdfAlignment.LEFT ->
                    Element.ALIGN_LEFT

                PdfAlignment.CENTER ->
                    Element.ALIGN_CENTER

                PdfAlignment.RIGHT ->
                    Element.ALIGN_RIGHT
            }

        /*
         * Row appearance.
         */
        when (row.type) {

            PdfRowType.NORMAL -> {

                pdfCell.backgroundColor =
                    RGBColor(
                        255,
                        255,
                        255
                    )
            }

            PdfRowType.HEADER -> {

                pdfCell.backgroundColor =
                    RGBColor(
                        0,
                        0,
                        0
                    )
            }

            PdfRowType.TOTAL -> {

                pdfCell.backgroundColor =
                    RGBColor(
                        245,
                        242,
                        235
                    )
            }
        }

        return pdfCell
    }

    private fun fontFor(
        text: PdfText
    ): Font {

        return when (text.type) {

            /*
             * Small gold heading.
             *
             * Example:
             *
             * PURCHASER
             * PACKING AND DESPATCH
             */
            PdfTextType.LABEL ->
                FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    8.2f,
                    RGBColor(
                        145,
                        105,
                        55
                    )
                )

            /*
             * Larger bold second heading.
             *
             * Example:
             *
             * De Ferranti Limited
             * Mondiconexus, Unipessoal, Lda
             */
            PdfTextType.HEADING ->
                FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    9.6f,
                    RGBColor(
                        20,
                        20,
                        20
                    )
                )

            /*
             * Normal body text.
             */
            PdfTextType.NORMAL ->
                FontFactory.getFont(
                    FontFactory.HELVETICA,
                    8.6f,
                    RGBColor(
                        28,
                        28,
                        28
                    )
                )

            /*
             * Bold body text.
             */
            PdfTextType.BOLD ->
                FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    8.6f,
                    RGBColor(
                        20,
                        20,
                        20
                    )
                )

            /*
             * Small text.
             *
             * Useful for control notes, etc.
             */
            PdfTextType.SMALL ->
                FontFactory.getFont(
                    FontFactory.HELVETICA,
                    7.2f,
                    RGBColor(
                        35,
                        35,
                        35
                    )
                )

            /*
             * White text intended for black
             * table header rows.
             */
            PdfTextType.TABLE_HEADER ->
                FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    7.1f,
                    RGBColor(
                        255,
                        255,
                        255
                    )
                )
        }
    }

    private class FooterPageEvent(
        private val footer: PdfFooter
    ) : PdfPageEventHelper() {

        override fun onEndPage(
            writer: PdfWriter,
            document: Document
        ) {

            val font =
                FontFactory.getFont(
                    FontFactory.HELVETICA,
                    7f,
                    RGBColor(
                        0,
                        0,
                        0
                    )
                )

            val table =
                PdfPTable(2)

            table.widthPercentage =
                100f

            table.totalWidth =
                document.pageSize.width -
                        document.leftMargin() -
                        document.rightMargin()

            table.isLockedWidth =
                true

            /*
             * Left side of footer.
             */
            val left =
                PdfPCell(
                    Phrase(
                        footer.leftText,
                        font
                    )
                )

            left.border =
                PdfPCell.TOP

            left.paddingTop =
                5f

            left.horizontalAlignment =
                Element.ALIGN_LEFT

            table.addCell(
                left
            )

            /*
             * Right side of footer.
             */
            val rightText =
                if (footer.showPageNumber) {
                    "Page ${writer.pageNumber}"
                } else {
                    ""
                }

            val right =
                PdfPCell(
                    Phrase(
                        rightText,
                        font
                    )
                )

            right.border =
                PdfPCell.TOP

            right.paddingTop =
                5f

            right.horizontalAlignment =
                Element.ALIGN_RIGHT

            table.addCell(
                right
            )

            table.writeSelectedRows(
                0,
                -1,
                document.leftMargin(),
                document.bottomMargin() - 10f,
                writer.directContent
            )
        }
    }
}
