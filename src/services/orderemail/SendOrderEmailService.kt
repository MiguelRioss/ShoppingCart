package services.orderemail

import db.OrderRepository
import kotlinx.serialization.json.*
import services.pdf.*

class OrderEmailNotFoundException : RuntimeException("Order not found")

/** Generates bytes before calling EMAIL_SENDER; never writes the PDF to disk. */
class SendOrderEmailService(
    private val orders: OrderRepository,
    private val builder: OrderEmailPayloadBuilder,
    private val sender: EmailSenderClient,
    private val generatePdf: (OrderEmailTemplate, DeFerrantiOrderDocuments) -> ByteArray = { template, data ->
        when(template) {
            OrderEmailTemplate.ORDER_CONFIRMATION -> CommercialInvoicePdf().generate(data)
            OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER -> PurchaseOrderPdf().generate(data)
            OrderEmailTemplate.SUPPLIER_PACKING_LIST -> PackingListPdf().generate(data)
            OrderEmailTemplate.SUPPLIER_DELIVERY_NOTE -> DeliveryNotePdf().generate(data)
            else -> error("This template does not require a PDF")
        }
    }
) {
    fun prepare(orderId:String,template:OrderEmailTemplate,context:JsonObject,documentData:DeFerrantiOrderDocuments?):JsonObject {
        val order=orders.findForEmail(orderId)?:throw OrderEmailNotFoundException()
        var attachment:EmailPdfAttachment?=null
        var payloadContext=context
        if(template.documentType!=null) {
            val data=documentData?:throw EmailPayloadValidationException(listOf("documentData"))
            if(data.references.customerOrderNumber!=orderId)throw EmailPayloadValidationException(listOf("documentData.references.customerOrderNumber (must match order)"))
            val number=when(template) {
                OrderEmailTemplate.ORDER_CONFIRMATION -> data.references.invoiceNumber
                OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER -> data.references.purchaseOrderNumber
                OrderEmailTemplate.SUPPLIER_PACKING_LIST -> data.references.packingListNumber
                OrderEmailTemplate.SUPPLIER_DELIVERY_NOTE -> data.references.deliveryNoteNumber
                else -> null
            }?.takeIf{it.isNotBlank()}?:throw EmailPayloadValidationException(listOf("documentData.references.${template.documentType} number"))
            val field=when(template) {
                OrderEmailTemplate.ORDER_CONFIRMATION -> "invoice_number"
                OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER -> "po_number"
                OrderEmailTemplate.SUPPLIER_PACKING_LIST -> "packing_list_number"
                else -> "delivery_note_number"
            }
            val fields=context["fields"] as? JsonObject?:JsonObject(emptyMap())
            val supplied=(fields[field] as? JsonPrimitive)?.contentOrNull
            if(supplied!=null&&supplied!=number)throw EmailPayloadValidationException(listOf("context.fields.$field (must match generated PDF)"))
            val mergedFields=fields.toMutableMap()
            mergedFields[field]=JsonPrimitive(number)
            fun fromDocument(key:String,value:String,date:Boolean=false) {
                if(value.isBlank())return
                val normalized=if(date) runCatching {java.time.LocalDate.parse(value)}.recoverCatching {java.time.LocalDate.parse(value,java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}.getOrElse{throw EmailPayloadValidationException(listOf("documentData.$key (invalid date)"))}.toString() else value
                val current=(fields[key] as? JsonPrimitive)?.contentOrNull
                if(current!=null&&current!=normalized)throw EmailPayloadValidationException(listOf("context.fields.$key (must match generated PDF)"))
                mergedFields[key]=JsonPrimitive(normalized)
            }
            when(template) {
                OrderEmailTemplate.ORDER_CONFIRMATION -> fromDocument("expected_despatch_date",data.references.requiredDespatchDate,true)
                OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER -> fromDocument("required_despatch_date",data.references.requiredDespatchDate,true)
                OrderEmailTemplate.SUPPLIER_PACKING_LIST -> {
                    fromDocument("package_count",data.packing.packageCount);fromDocument("gross_weight",data.packing.grossWeight);fromDocument("package_dimensions",data.packing.dimensions)
                }
                OrderEmailTemplate.SUPPLIER_DELIVERY_NOTE -> {
                    fromDocument("courier",data.shipment.courier);fromDocument("tracking_number",data.shipment.trackingNumber);fromDocument("incoterm",data.shipment.incoterm)
                }
                else -> Unit
            }
            payloadContext=JsonObject(context + ("fields" to JsonObject(mergedFields)))
            attachment=EmailPdfAttachment("$orderId:${template.documentType}:$number",template.documentType,"${template.documentType}.pdf",generatePdf(template,data))
            attachment.toJson() // Invalid/oversized output must never reach the sender.
        } else if(documentData!=null) throw EmailPayloadValidationException(listOf("documentData (not required for this template)"))
        val payload=builder.prepare(template,order,payloadContext,attachment)
        if(documentData!=null && template.productCards) {
            val products=payload.getValue("variables").jsonObject.getValue("products").jsonArray
            val expected=products.map{it.jsonObject.getValue("name").jsonPrimitive.content}.sorted()
            if(documentData.goods.map{it.productName}.sorted()!=expected)throw EmailPayloadValidationException(listOf("documentData.goods (must match selected saved order product names)"))
        }
        return payload
    }

    fun send(orderId:String,template:OrderEmailTemplate,context:JsonObject,documentData:DeFerrantiOrderDocuments?):JsonObject {
        val payload = prepare(orderId,template,context,documentData)
        val result=sender.send(payload)
        return buildJsonObject {
            put("status","submitted");put("templateId",template.id);put("orderId",orderId)
            put("idempotencyKey",payload.getValue("idempotencyKey"));put("emailSender",result)
        }
    }
}
