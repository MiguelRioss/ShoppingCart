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
import services.pdf.DeFerrantiOrderDocuments
import productdatabaseaccesslayer.ProductDataAccess
import productdatabaseaccesslayer.ProductCatalogDataSource

class OrderManager(
    private val orderRepository: OrderRepository,
    private val products: ProductDataAccess = ProductCatalogDataSource()
) : OrderService {
    fun invoicePreview(orderId: String, paidSnapshot: JsonObject? = null): ByteArray {
        return OrderPdfData.Invoice(orderDocuments(orderId, paidSnapshot)).generate()
    }
    fun purchaseOrderPreview(orderId: String, paidSnapshot: JsonObject? = null): ByteArray =
        OrderPdfData.PurchaseOrder(orderDocuments(orderId, paidSnapshot)).generate()
    fun deliveryNotePreview(orderId: String, paidSnapshot: JsonObject? = null): ByteArray =
        OrderPdfData.DeliveryNote(orderDocuments(orderId, paidSnapshot)).generate()
    fun packingListPreview(orderId: String, paidSnapshot: JsonObject? = null): ByteArray =
        OrderPdfData.PackingList(orderDocuments(orderId, paidSnapshot)).generate()

    fun supplierContact(orderId: String): Pair<String, String> {
        val snapshot = orderRepository.findForEmail(orderId)?.get("snapshot") as? JsonObject
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val productId = (snapshot["items"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("productId")
            ?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
            ?: throw IllegalArgumentException("Order products are missing")
        val product = products.getProductDetailsById(productId)?.let { Json.parseToJsonElement(it).jsonObject }
            ?: throw IllegalArgumentException("Product not found: $productId")
        val supplier = product["supplier"] as? JsonObject ?: throw IllegalArgumentException("Supplier is missing")
        val name = (supplier["contact_name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            ?: (supplier["name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Supplier contact name is missing")
        val email = (supplier["email"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Supplier email is missing")
        return name to email
    }

    private fun orderDocuments(orderId: String, snapshotOverride: JsonObject?): DeFerrantiOrderDocuments {
        val order = orderRepository.findForEmail(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")
        require((order["status"] as? JsonPrimitive)?.content == "PAID") { "Order documents require a paid order" }
        val snapshot = snapshotOverride ?: order["snapshot"] as? JsonObject
            ?: throw IllegalArgumentException("Order snapshot is missing")
        val checkoutId = (order["checkoutSessionId"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("Order checkout session is missing")
        val bookings = orderRepository.shipmentBookings(checkoutId).mapNotNull { it as? JsonObject }
        require(bookings.isNotEmpty() && bookings.all { booking ->
            val details = booking["booking"] as? JsonObject ?: booking
            (booking["status"] as? JsonPrimitive)?.contentOrNull == "BOOKED" &&
                !(details["masterTrackingNumber"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()
        }) { "FedEx AWB is required before generating order documents" }
        val bookingDetails = bookings.map { it["booking"] as? JsonObject ?: it }
        val awb = bookingDetails.mapNotNull { (it["masterTrackingNumber"] as? JsonPrimitive)?.contentOrNull }.distinct().joinToString(", ")
        val trackingNumbers = bookingDetails.flatMap { details ->
            (details["packages"] as? JsonArray).orEmpty().mapNotNull { packageInfo ->
                ((packageInfo as? JsonObject)?.get("trackingNumber") as? JsonPrimitive)?.contentOrNull
            }
        }.distinct().ifEmpty { bookingDetails.mapNotNull { (it["masterTrackingNumber"] as? JsonPrimitive)?.contentOrNull } }.joinToString(", ")
        return OrderInvoicePreview(products).build(
            orderId,
            snapshot,
            invoiceNumber(orderId),
            invoiceIssuer(orderId),
            despatchEstimate(orderId, snapshot) ?: throw IllegalArgumentException("Product lead time is missing"),
            trackingNumbers,
            awb
        )
    }
    fun invoiceNumber(orderId: String): String = orderRepository.invoiceNumberForOrder(orderId)
    fun purchaseOrderNumber(orderId: String): String = "PO-$orderId"
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
