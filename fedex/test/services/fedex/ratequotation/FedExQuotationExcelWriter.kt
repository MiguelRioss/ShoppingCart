package services.fedex.ratequotation

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import services.shipping.fedex.ratequotation.FedExCountryQuotationResult

class FedExQuotationExcelWriter {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun write(
        results: List<FedExCountryQuotationResult>,
        outputFile: File
    ) {
        val workbook =
            XSSFWorkbook()

        val wrapStyle =
            workbook.createCellStyle().apply {
                wrapText = true
                verticalAlignment = VerticalAlignment.TOP
            }

        val sheet =
            workbook.createSheet(
                "FedEx Quotations"
            )

        val header =
            sheet.createRow(0)

        val headings =
            listOf(
                "Country",
                "Country Code",
                "City",
                "Postcode",
                "Quotation Available",
                "Attempts",
                "Shipment Options Price EUR",
                "FedEx Response",
                "Error"
            )

        headings.forEachIndexed { index, heading ->
            header
                .createCell(index)
                .setCellValue(heading)
        }

        results.forEachIndexed { index, result ->
            val row =
                sheet.createRow(
                    index + 1
                )

            row.createCell(0)
                .setCellValue(
                    result.countryName
                )

            row.createCell(1)
                .setCellValue(
                    result.countryCode
                )

            row.createCell(2)
                .setCellValue(
                    result.city
                )

            row.createCell(3)
                .setCellValue(
                    result.postalCode
                )

            row.createCell(4)
                .setCellValue(
                    if (result.success) {
                        "YES"
                    } else {
                        "NO"
                    }
                )

            row.createCell(5)
                .setCellValue(
                    result.attempts.toDouble()
                )

            row.createCell(6)
                .apply {
                    cellStyle = wrapStyle
                    setCellValue(
                        shipmentOptions(result.response)
                    )
                }

            row.createCell(7)
                .apply {
                    cellStyle = wrapStyle
                    setCellValue(
                        result.response ?: ""
                    )
                }

            row.createCell(8)
                .apply {
                    cellStyle = wrapStyle
                    setCellValue(
                        result.error ?: ""
                    )
                }
        }

        sheet.setColumnWidth(0, 6_000)
        sheet.setColumnWidth(1, 4_000)
        sheet.setColumnWidth(2, 6_000)
        sheet.setColumnWidth(3, 5_000)
        sheet.setColumnWidth(4, 5_500)
        sheet.setColumnWidth(5, 3_500)
        sheet.setColumnWidth(6, 16_000)
        sheet.setColumnWidth(7, 20_000)
        sheet.setColumnWidth(8, 20_000)

        outputFile
            .parentFile
            ?.mkdirs()

        FileOutputStream(
            outputFile
        ).use { output ->
            workbook.write(output)
        }

        workbook.close()

        println()
        println(
            "Excel written to:"
        )

        println(
            outputFile.absolutePath
        )
    }

    private fun shipmentOptions(
        response: String?
    ): String {
        if (response.isNullOrBlank()) {
            return ""
        }

        val root =
            runCatching {
                json.parseToJsonElement(response) as JsonObject
            }.getOrNull()
                ?: return ""

        val output =
            root["output"] as? JsonObject
                ?: return ""

        val rateReplyDetails =
            output["rateReplyDetails"] as? JsonArray
                ?: return ""

        return rateReplyDetails
            .mapNotNull { detail ->
                formatShipmentOption(
                    detail as? JsonObject
                )
            }
            .joinToString("\n")
    }

    private fun formatShipmentOption(
        detail: JsonObject?
    ): String? {
        if (detail == null) {
            return null
        }

        val serviceName =
            primitiveContent(detail["serviceName"])
                ?: primitiveContent(detail["serviceType"])
                ?: return null

        val ratedShipment =
            (detail["ratedShipmentDetails"] as? JsonArray)
                ?.firstOrNull() as? JsonObject
                ?: return serviceName

        val charge =
            primitiveNumber(ratedShipment["totalNetCharge"])
                ?: primitiveNumber(ratedShipment["totalNetFedExCharge"])
                ?: primitiveNumber(ratedShipment["totalBaseCharge"])

        val eurCharge =
            charge?.let {
                amountInEur(
                    amount = it,
                    ratedShipment = ratedShipment
                )
            }

        return if (eurCharge == null) {
            serviceName
        } else {
            "$serviceName - ${formatAmount(eurCharge)} EUR"
        }
    }

    private fun amountInEur(
        amount: Double,
        ratedShipment: JsonObject
    ): Double? {
        val currency =
            primitiveContent(ratedShipment["currency"])
                ?: ((ratedShipment["shipmentRateDetail"] as? JsonObject)
                    ?.get("currency")
                    ?.let(::primitiveContent))
                ?: "EUR"

        if (currency.equals("EUR", ignoreCase = true)) {
            return amount
        }

        val exchangeRate =
            ((ratedShipment["shipmentRateDetail"] as? JsonObject)
                ?.get("currencyExchangeRate") as? JsonObject)
                ?: return null

        val fromCurrency =
            primitiveContent(exchangeRate["fromCurrency"])

        val intoCurrency =
            primitiveContent(exchangeRate["intoCurrency"])

        val rate =
            primitiveNumber(exchangeRate["rate"])
                ?: return null

        return when {
            fromCurrency.equals("EUR", ignoreCase = true) &&
                    intoCurrency.equals(currency, ignoreCase = true) ->
                amount / rate

            fromCurrency.equals(currency, ignoreCase = true) &&
                    intoCurrency.equals("EUR", ignoreCase = true) ->
                amount * rate

            else ->
                null
        }
    }
    private fun primitiveContent(
        value: Any?
    ): String? =
        (value as? JsonPrimitive)
            ?.content

    private fun primitiveNumber(
        value: Any?
    ): Double? =
        (value as? JsonPrimitive)
            ?.doubleOrNull

    private fun formatAmount(
        amount: Double
    ): String =
        if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            "%.2f".format(Locale.US, amount)
        }
}
