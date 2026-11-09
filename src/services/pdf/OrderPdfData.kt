package services.pdf

/** Validated inputs for each of the four PDFs. A missing value is reported by field name. */
sealed interface OrderPdfData {
    val documents: DeFerrantiOrderDocuments

    data class Invoice(override val documents: DeFerrantiOrderDocuments) : OrderPdfData {
        init { requireFields(documents, invoiceFields(documents)) }
        fun generate(): ByteArray = CommercialInvoicePdf().generate(documents)
    }

    data class PurchaseOrder(override val documents: DeFerrantiOrderDocuments) : OrderPdfData {
        init { requireFields(documents, purchaseOrderFields(documents)) }
        fun generate(): ByteArray = PurchaseOrderPdf().generate(documents)
    }

    data class PackingList(override val documents: DeFerrantiOrderDocuments) : OrderPdfData {
        init { requireFields(documents, packingListFields(documents)) }
        fun generate(): ByteArray = PackingListPdf().generate(documents)
    }

    data class DeliveryNote(override val documents: DeFerrantiOrderDocuments) : OrderPdfData {
        init { requireFields(documents, deliveryNoteFields(documents)) }
        fun generate(): ByteArray = DeliveryNotePdf().generate(documents)
    }
}

private fun requireFields(documents: DeFerrantiOrderDocuments, fields: List<Pair<String, String?>>) {
    val missing = fields.filter { (_, value) -> value.isNullOrBlank() || value.trim().startsWith("[") }
        .map { it.first }
    require(missing.isEmpty()) { "Missing PDF fields: ${missing.joinToString()}" }
    require(documents.goods.isNotEmpty()) { "Missing PDF fields: goods" }
}

private fun commonFields(d: DeFerrantiOrderDocuments) = listOf(
    "references.customerOrderNumber" to d.references.customerOrderNumber,
    "parties.purchaser.name" to d.parties.purchaser.name,
    "parties.purchaser.addressLines" to d.parties.purchaser.addressLines.joinToString(),
    "parties.deliverTo.name" to d.parties.deliverTo.name,
    "parties.deliverTo.addressLines" to d.parties.deliverTo.addressLines.joinToString()
) + d.goods.flatMapIndexed { index, line -> listOf(
    "goods[$index].productName" to line.productName,
    "goods[$index].boxes" to line.boxes,
    "goods[$index].lineTotal" to line.lineTotal
) }

private fun invoiceFields(d: DeFerrantiOrderDocuments) = commonFields(d) + listOf(
    "references.invoiceNumber" to d.references.invoiceNumber,
    "references.purchaseOrderDate" to d.references.purchaseOrderDate,
    "parties.seller.name" to d.parties.seller.name,
    "parties.seller.addressLines" to d.parties.seller.addressLines.joinToString(),
    "parties.importerOfRecord.name" to d.parties.importerOfRecord.name,
    "totals.purchaseOrderTotal" to d.totals.purchaseOrderTotal,
    "totals.vatTreatment" to d.totals.vatTreatment
) + d.goods.flatMapIndexed { index, line -> listOf(
    "goods[$index].hsCode" to line.hsCode,
    "goods[$index].originCountry" to line.originCountry,
    "goods[$index].unitPrice" to line.unitPrice
) }

private fun purchaseOrderFields(d: DeFerrantiOrderDocuments) = commonFields(d) + listOf(
    "references.purchaseOrderNumber" to d.references.purchaseOrderNumber,
    "references.purchaseOrderDate" to d.references.purchaseOrderDate,
    "references.requiredDespatchDate" to d.references.requiredDespatchDate,
    "parties.supplier.name" to d.parties.supplier.name,
    "parties.supplier.addressLines" to d.parties.supplier.addressLines.joinToString(),
    "totals.supplierSubtotal" to d.totals.supplierSubtotal,
    "packing.packageCount" to d.packing.packageCount,
    "packing.dimensions" to d.packing.dimensions,
    "packing.grossWeight" to d.packing.grossWeight
) + d.goods.mapIndexed { index, line -> "goods[$index].supplierUnitPrice" to line.unitPrice }

private fun packingListFields(d: DeFerrantiOrderDocuments) = commonFields(d) + listOf(
    "references.packingListNumber" to d.references.packingListNumber,
    "references.packingDate" to d.references.packingDate,
    "packing.packageType" to d.packing.packageType,
    "packing.packageCount" to d.packing.packageCount,
    "packing.dimensions" to d.packing.dimensions,
    "packing.grossWeight" to d.packing.grossWeight,
    "shipment.courier" to d.shipment.courier,
    "shipment.service" to d.shipment.service
)

private fun deliveryNoteFields(d: DeFerrantiOrderDocuments) = commonFields(d) + listOf(
    "references.deliveryNoteNumber" to d.references.deliveryNoteNumber,
    "references.deliveryDate" to d.references.deliveryDate,
    "shipment.courier" to d.shipment.courier,
    "shipment.trackingNumber" to d.shipment.trackingNumber,
    "shipment.incoterm" to d.shipment.incoterm,
    "packing.packageCount" to d.packing.packageCount,
    "packing.dimensions" to d.packing.dimensions
)
