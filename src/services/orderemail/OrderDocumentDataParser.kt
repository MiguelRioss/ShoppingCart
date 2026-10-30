package services.orderemail

import config.Environment
import services.pdf.*
import kotlinx.serialization.json.*

/** Explicit fulfilment data; never uses the sample/test PDF builders or caller-supplied file paths. */
object OrderDocumentDataParser {
    fun parse(input: JsonObject): DeFerrantiOrderDocuments {
        val refs=input.obj("references")
        val parties=input.obj("parties")
        val packing=input.obj("packing")
        val shipment=input.obj("shipment")
        val totals=input.obj("totals")
        val checks=input["checks"] as? JsonObject ?: JsonObject(emptyMap())
        val notes=input["notes"] as? JsonObject ?: JsonObject(emptyMap())
        val goods=(input["goods"] as? JsonArray)?.mapIndexed { index, value ->
            val line=value as? JsonObject ?: invalid("documentData.goods[$index]")
            DocumentGoodsLine(
                productName=line.required("productName"), specificationLines=line.lines("specificationLines"),
                area=line.optional("area").orEmpty(), boxes=line.optional("boxes").orEmpty(), tiles=line.optional("tiles").orEmpty(),
                unitPrice=line.optional("unitPrice"), lineTotal=line.optional("lineTotal"), unitPriceLabel=line.optional("unitPriceLabel")?:"per m2",
                deFerrantiSku=line.optional("deFerrantiSku"), supplierProductId=line.optional("supplierProductId"),
                hsCode=line.optional("hsCode"), originCountry=line.optional("originCountry"), netWeight=line.optional("netWeight"),
                grossWeight=line.optional("grossWeight"), packageDescription=line.optional("packageDescription"), standardisedProductId=line.optional("standardisedProductId")
            )
        }?.takeIf { it.isNotEmpty() } ?: invalid("documentData.goods")
        return DeFerrantiOrderDocuments(
            branding=DocumentBranding(Environment.get("DEFERRANTI_LOGO_PATH")?:"src/main/resources/deferranti-logo.png"),
            references=DocumentReferences(
                refs.optional("purchaseOrderNumber").orEmpty(),refs.optional("purchaseOrderDate").orEmpty(),refs.required("customerOrderNumber"),
                refs.optional("requiredDespatchDate").orEmpty(),refs.optional("packingListNumber").orEmpty(),refs.optional("packingDate").orEmpty(),
                refs.optional("deliveryNoteNumber").orEmpty(),refs.optional("deliveryDate").orEmpty(),refs.optional("invoiceNumber")
            ),
            parties=DocumentParties(parties.party("purchaser"),parties.party("supplier"),parties.party("shipFrom"),parties.party("deliverTo"),parties.party("importerOfRecord"),parties.party("seller")),
            goods=goods,
            totals=DocumentTotals(totals.required("supplierSubtotal"),totals.required("vatTreatment"),totals.required("purchaseOrderTotal")),
            packing=PackingDetails(packing.optional("packageType").orEmpty(),packing.optional("packageCount").orEmpty(),packing.optional("dimensions").orEmpty(),packing.optional("grossWeight").orEmpty(),packing.optional("contents").orEmpty(),packing.optional("packingCost"),packing.lines("handlingLines")),
            shipment=ShipmentDetails(shipment.optional("courier").orEmpty(),shipment.optional("service").orEmpty(),shipment.optional("trackingNumber").orEmpty(),shipment.optional("awb").orEmpty(),shipment.optional("purpose").orEmpty(),shipment.optional("incoterm").orEmpty(),shipment.optional("deliveryTerms").orEmpty()),
            checks=DocumentChecks(checks.lines("supplierConfirmationLines"),checks.lines("authorisationLines"),checks.lines("packedByLines"),checks.lines("finalCheckLines"),checks.lines("deliveryConditionLines"),checks.lines("deliveryExceptionLines"),checks.lines("receivedByLines"),checks.lines("courierReceiptLines")),
            notes=DocumentNotes(notes.lines("requiredDocumentLines"),notes.lines("b2bB2cLines"),notes.lines("deliveryImportantLines"),notes.lines("documentMatchLines"),notes.optional("purchaseOrderControlNote").orEmpty(),notes.optional("packingListControlNote").orEmpty(),notes.optional("deliveryNoteControlNote").orEmpty())
        )
    }
    private fun JsonObject.party(key:String):DocumentParty {
        val value=obj(key)
        val address=value.lines("addressLines").takeIf { it.isNotEmpty() }?:invalid("documentData.parties.$key.addressLines")
        return DocumentParty(value.required("name"),address,value.optional("contactName"),value.optional("phone"),value.optional("email"),value.optional("customerType"),value.optional("vatNumber"),value.optional("eoriNumber"))
    }
    private fun JsonObject.obj(key:String)=this[key] as? JsonObject?:invalid("documentData.$key")
    private fun JsonObject.optional(key:String):String? {
        val value=this[key]?:return null
        if(value is JsonNull)return null
        return (value as? JsonPrimitive)?.contentOrNull?.takeIf{it.isNotBlank()}?:invalid("documentData.$key")
    }
    private fun JsonObject.required(key:String)=optional(key)?:invalid("documentData.$key")
    private fun JsonObject.lines(key:String):List<String> {
        val value=this[key]?:return emptyList()
        return (value as? JsonArray)?.map{(it as? JsonPrimitive)?.contentOrNull?:invalid("documentData.$key")}?:invalid("documentData.$key")
    }
    private fun invalid(field:String):Nothing=throw EmailPayloadValidationException(listOf(field))
}
