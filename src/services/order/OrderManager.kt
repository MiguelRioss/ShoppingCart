package services.order

import db.OrderRepository
import kotlinx.serialization.json.contentOrNull
import domain.cart.ShoppingCart
import domain.checkout.CheckoutLineItem
import domain.checkout.CheckoutShippingCharge
import domain.checkout.CreateCheckoutSessionRequest
import services.checkout.core.orderSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import services.pdf.DocumentParty
import services.pdf.OrderPdfData
import productdatabaseaccesslayer.ProductDataAccess
import productdatabaseaccesslayer.ProductCatalogDataSource

class OrderManager(
    private val orderRepository: OrderRepository,
    private val products: ProductDataAccess = ProductCatalogDataSource()
) : OrderService {
    fun invoicePreview(orderId: String, paidSnapshot: JsonObject? = null): ByteArray {
        val order = orderRepository.findForEmail(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")
        require((order["status"] as? JsonPrimitive)?.content == "PAID") { "Invoice preview requires a paid order" }
        val snapshot = paidSnapshot ?: order["snapshot"] as? JsonObject
            ?: throw IllegalArgumentException("Order snapshot is missing")
        val document = OrderInvoicePreview(products).build(
            orderId,
            snapshot,
            invoiceNumber(orderId),
            invoiceIssuer(orderId),
            despatchEstimate(orderId, snapshot) ?: throw IllegalArgumentException("Product lead time is missing")
        )
        return OrderPdfData.Invoice(document).generate()
    }
    fun invoiceNumber(orderId: String): String = orderRepository.invoiceNumberForOrder(orderId)
    fun invoiceIssuer(orderId: String): DocumentParty {
        val snapshot = orderRepository.findForEmail(orderId)?.get("snapshot") as? JsonObject
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val destination = snapshot["deliveryAddress"] as? JsonObject
        val account = (snapshot["invoiceIssuerAccount"] as? JsonPrimitive)?.contentOrNull
            ?: (destination?.get("country") as? JsonPrimitive)?.contentOrNull?.let { country ->
                if (country == "GB") "UK" else "PT"
            }
            ?: throw IllegalArgumentException("Invoice issuer account is missing for order $orderId")
        return InvoiceIssuers.forAccount(account)
    }
    fun importerOfRecord(orderId: String): DocumentParty = invoiceIssuer(orderId)
    fun despatchEstimate(orderId: String, snapshotOverride: JsonObject? = null): String? {
        val snapshot = snapshotOverride ?: orderRepository.findForEmail(orderId)?.get("snapshot") as? JsonObject
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val items = (snapshot["items"] as? JsonArray).orEmpty().map { it.jsonObject }
            .filter { (it["productId"] as? JsonPrimitive)?.content != "0" }
        if (items.isEmpty()) return null
        val estimates = items.map { (it["generalLeadTime"] as? JsonPrimitive)?.contentOrNull?.trim() }
        if (estimates.any { it.isNullOrBlank() }) return null
        return estimates.filterNotNull().distinct().singleOrNull()
            ?: items.zip(estimates).joinToString("; ") { (item, estimate) ->
                "${(item["name"] as? JsonPrimitive)?.contentOrNull ?: "Product"}: $estimate"
            }
    }

    fun withProductLeadTimes(snapshot: JsonObject): JsonObject {
        val items = snapshot["items"] as? JsonArray ?: return snapshot
        val updated = items.map { element ->
            val item = element.jsonObject
            if ((item["generalLeadTime"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true) return@map element
            val productId = (item["productId"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?.takeIf { it != 0L } ?: return@map element
            val details = products.getProductDetailsById(productId) ?: return@map element
            val product = Json.parseToJsonElement(details).jsonObject
            val purchase = product["purchase_information"] as? JsonObject
            val lead = ((purchase?.get("lead_times_and_quotation") as? JsonObject)?.get("general_lead_time")
                ?: (purchase?.get("order") as? JsonObject)?.get("product_lead_time"))
                ?.let { it as? JsonPrimitive }?.contentOrNull?.takeIf(String::isNotBlank)
                ?: return@map element
            JsonObject(item + ("generalLeadTime" to JsonPrimitive(lead)))
        }
        return JsonObject(snapshot + ("items" to JsonArray(updated)))
    }

    fun missingInvoiceFields(orderId: String): List<String> {
        val order = orderRepository.findForEmail(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val snapshot = order["snapshot"] as? kotlinx.serialization.json.JsonObject
            ?: return listOf("order.snapshot")
        val address = snapshot["deliveryAddress"] as? kotlinx.serialization.json.JsonObject
        fun text(source: kotlinx.serialization.json.JsonObject?, key: String): String? =
            (source?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        return buildList {
            if (text(snapshot, "customerEmail") == null) add("customer.email")
            if (text(snapshot, "customerFirstName") == null) add("customer.name")
            for (field in listOf("addressLine1", "townOrCity", "postcode", "country")) {
                if (text(address, field) == null) add("deliveryAddress.$field")
            }
            if ((snapshot["items"] as? kotlinx.serialization.json.JsonArray).isNullOrEmpty()) add("order.items")
            if (text(snapshot, "total") == null) add("order.total")
            if (text(snapshot, "currency") == null) add("order.currency")
            add("invoice.taxTreatment")
            if (despatchEstimate(orderId) == null) add("invoice.despatchEstimate")
            if (runCatching { invoiceIssuer(orderId) }.isFailure) add("invoice.seller")
            add("invoice.supplier")
            add("invoice.shipFrom")
        }
    }

    override fun createForCheckout(
        checkoutId: String,
        request: CreateCheckoutSessionRequest,
        cart: ShoppingCart,
        productLineItems: List<CheckoutLineItem>,
        shippingCharge: CheckoutShippingCharge?
    ) {
        val snapshot = orderSnapshot(request, cart, productLineItems, shippingCharge)
        val issuerAccount = if (request.deliveryAddress?.country == "GB") "UK" else "PT"
        orderRepository.create(
            checkoutId,
            request.userId,
            JsonObject(snapshot + mapOf(
                "taxAmount" to JsonPrimitive("0.00"),
                "taxCurrency" to JsonPrimitive("eur"),
                "taxCalculation" to JsonPrimitive("temporary_zero"),
                "invoiceIssuerAccount" to JsonPrimitive(issuerAccount)
            ))
        )
    }
}
