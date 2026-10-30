package services.orderemail

/** Stable IDs and rendering requirements; this registry does not send or schedule emails. */
enum class OrderEmailTemplate(
    val id: String,
    val requiredFields: Set<String>,
    val supplier: Boolean = false,
    val productCards: Boolean = false,
    val documentType: String? = null
) {
    PAYMENT_CONFIRMATION("payment-confirmation", setOf("lead_time_summary"), productCards = true),
    ORDER_CONFIRMATION("order-confirmation", setOf("expected_despatch_date", "invoice_number"), productCards = true, documentType = "invoice"),
    SUPPLIER_PURCHASE_ORDER("supplier-purchase-order", setOf("po_number", "required_despatch_date"), supplier = true, productCards = true, documentType = "purchase-order"),
    INCOMPLETE_CHECKOUT("incomplete-checkout", emptySet(), productCards = true),
    SUPPLIER_PACKING_LIST("supplier-packing-list", setOf("packing_list_number", "quantity_units", "package_count", "gross_weight", "package_dimensions"), supplier = true, productCards = true, documentType = "packing-list"),
    SUPPLIER_DELIVERY_NOTE("supplier-delivery-note", setOf("delivery_note_number", "courier", "tracking_number", "incoterm"), supplier = true, documentType = "delivery-note"),
    REVISED_DESPATCH_DATE("revised-despatch-date", setOf("previous_despatch_date", "revised_despatch_date")),
    GOODS_DESPATCHED("goods-despatched", setOf("courier", "tracking_number", "estimated_delivery_date", "tracking_url")),
    DELIVERY_CONFIRMATION("delivery-confirmation", setOf("delivery_date")),
    POST_DELIVERY_THANK_YOU("post-delivery-thank-you", setOf("discount_code", "marketing_consent_link"));

    companion object { fun fromId(id: String) = entries.find { it.id == id } }
}

class EmailPayloadValidationException(val fields: List<String>) : IllegalArgumentException("Missing or invalid email data: ${fields.joinToString()}")
class EmailPayloadConflictException(message: String) : IllegalArgumentException(message)
