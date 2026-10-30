package services.orderemail

import config.Environment
import kotlinx.serialization.json.*
import services.pdf.model.*
import java.math.BigDecimal

/** Uses paid-order values, never the sample customs-document data. */
class PaymentConfirmationPayload(private val builder: OrderEmailPayloadBuilder) {
    fun prepare(order: JsonObject): JsonObject {
        val snapshot = order.getValue("snapshot").jsonObject
        val items = snapshot.getValue("items").jsonArray.map { it.jsonObject }
        val leadTimes = items.filter { it["productId"]?.jsonPrimitive?.content != "0" }.joinToString("\n") {
            val name = it.getValue("name").jsonPrimitive.content
            val lead = (it["generalLeadTime"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            "$name: ${lead ?: "To be confirmed"}"
        }
        val context = buildJsonObject { put("fields", buildJsonObject { put("lead_time_summary", leadTimes) }) }
        val payload = builder.prepare(OrderEmailTemplate.PAYMENT_CONFIRMATION, order, context)
        val id = order.getValue("id").jsonPrimitive.content
        val amount = order["amountPaidMinor"] ?: snapshot["amountPaidMinor"]
        val currency = (order["paidCurrency"] ?: snapshot["paidCurrency"])?.jsonPrimitive?.contentOrNull
        val paidMinor = (amount as? JsonPrimitive)?.longOrNull
            ?: throw EmailPayloadValidationException(listOf("amountPaidMinor"))
        if (currency.isNullOrBlank()) throw EmailPayloadValidationException(listOf("paidCurrency"))
        val digits = java.util.Currency.getInstance(currency.uppercase()).defaultFractionDigits
        val paid = BigDecimal.valueOf(paidMinor).movePointLeft(digits)
        val productLines = payload.getValue("variables").jsonObject.getValue("products").jsonArray.map { it.jsonObject }
        val rows = productLines.map {
            PdfRow.normal(PdfCell.text(it.getValue("name").jsonPrimitive.content),
                PdfCell.text("${it.getValue("quantity").jsonPrimitive.content} ${it.getValue("unit").jsonPrimitive.content}"),
                PdfCell.text("${it.getValue("lineTotal").jsonPrimitive.content} ${it.getValue("currency").jsonPrimitive.content.uppercase()}"))
        }
        val shipping = items.filter { it["productId"]?.jsonPrimitive?.content == "0" }.map {
            PdfRow.normal(PdfCell.text(it.getValue("name").jsonPrimitive.content), PdfCell.text(""),
                PdfCell.text("${it.getValue("amountTotal").jsonPrimitive.content} ${currency.uppercase()}"))
        }
        val document = PdfDocument(
            PdfHeader(Environment.get("DEFERRANTI_LOGO_PATH") ?: "src/main/resources/deferranti-logo.png", "ORDER PAYMENT SUMMARY"),
            listOf(
                PdfTable.equalColumns(PdfCell.text("Order: $id\nPayment confirmed\n${snapshot["customerEmail"]?.jsonPrimitive?.content.orEmpty()}")),
                PdfTable(rows = listOf(PdfRow.header(PdfCell.header("Product"), PdfCell.header("Quantity"), PdfCell.header("Line total"))) + rows + shipping),
                PdfTable.equalColumns(PdfCell.text("Paid: ${paid.toPlainString()} ${currency.uppercase()}")),
                PdfTable.equalColumns(PdfCell.text("Estimated despatch lead times:\n$leadTimes\nLead times are estimates, not confirmed shipping dates.\nThis payment summary is not a VAT or customs invoice."))
            ),
            PdfFooter("DE FERRANTI | ask@deferranti.com | www.deferranti.com")
        )
        val bytes = OpenPdfGenerator().generate(document)
        val attachment = EmailPdfAttachment("$id:payment-summary", "payment-summary", "payment-summary.pdf", bytes).toJson()
        return JsonObject(payload + ("attachments" to JsonArray(listOf(attachment))))
    }
}
