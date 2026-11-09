package services.order

import ShoppingCartProduct
import db.offline.InMemoryOrderRepository
import domain.cart.ShoppingCart
import domain.checkout.CheckoutDeliveryAddress
import domain.checkout.CheckoutLineItem
import domain.checkout.CreateCheckoutSessionRequest
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess
import services.pdf.OrderPdfData

/** A paid order persisted only in an isolated in-memory repository. */
internal class OrderSUT private constructor(
    val scenarioName: String,
    val id: String,
    val orders: InMemoryOrderRepository,
    val manager: OrderManager,
    val catalogue: ProductDataAccess
) {
    val order: JsonObject get() = requireNotNull(orders.findForEmail(id))

    fun labelledInvoicePdf(): ByteArray {
        val documents = OrderInvoicePreview(catalogue).build(
            id, order.getValue("snapshot").jsonObject, manager.invoiceNumber(id),
            manager.invoiceIssuer(id), requireNotNull(manager.despatchEstimate(id))
        )
        return OrderPdfData.Invoice(
            documents.copy(branding = documents.branding.copy(subtitle = "TEST DATA - CUSTOMER, PAYMENT AND FULFILMENT SIMULATED"))
        ).generate()
    }

    companion object {
        fun oneProductOrder(paid: Boolean = true): OrderSUT = create(paid = paid, scenarioName = "oneProductOrder")

        fun nineAndHalfProductOrder(): OrderSUT = create(
            scenarioName = "9_5m_ProductOrder",
            productAreas = linkedMapOf(
                9091L to BigDecimal("3.0"),
                9095L to BigDecimal("3.5"),
                9093L to BigDecimal("3.0")
            )
        )

        fun create(
            recipientEmail: String? = null,
            paid: Boolean = true,
            productAreas: Map<Long, BigDecimal> = mapOf(9091L to BigDecimal("2")),
            scenarioName: String = "oneProductOrder"
        ): OrderSUT {
            require(productAreas.isNotEmpty()) { "At least one product is required" }
            val products = productAreas.keys.associateWith { productId ->
                Json.parseToJsonElement(Files.readString(Path.of("test/services/order/fixtures/product-$productId.json"))).jsonObject
            }
            val input = Json.parseToJsonElement(Files.readString(Path.of("test/services/order/fixtures/checkout-input.json"))).jsonObject
            data class Line(val id: Long, val area: BigDecimal, val boxes: Int, val price: BigDecimal, val product: JsonObject, val leadTime: String?)
            val lines = productAreas.map { (productId, area) ->
                val product = products.getValue(productId)
                require(product.getValue("id").jsonPrimitive.content.toLong() == productId)
                val purchase = product.getValue("purchase_information").jsonObject
                val order = purchase.getValue("order").jsonObject
                val boxes = area.divide(order.getValue("m2_per_box").jsonPrimitive.content.toBigDecimal(), 0, RoundingMode.UNNECESSARY).toInt()
                require(boxes > 0)
                val price = order.getValue("client_price_per_m2").jsonPrimitive.content.toBigDecimal().multiply(area)
                val leadTime = purchase.getValue("lead_times_and_quotation").jsonObject["general_lead_time"]?.jsonPrimitive?.contentOrNull
                Line(productId, area, boxes, price, product, leadTime)
            }
            val total = lines.sumOf { it.price }
            val catalogue = object : ProductDataAccess {
                override fun getAllProducts() = products.values.joinToString(",", "[", "]")
                override fun getPurchasableProducts() = getAllProducts()
                override fun getProductById(productId: Long) = products[productId]?.toString()
                override fun getProductBySlug(productSlug: String) = requireNotNull(
                    products.values.find { it["slug"]?.jsonPrimitive?.contentOrNull == productSlug }
                ).toString()
                override fun getProductDetailsById(productId: Long) = getProductById(productId)
            }
            val orders = InMemoryOrderRepository()
            val manager = OrderManager(orders, catalogue)
            val id = UUID.randomUUID().toString()
            val cart = ShoppingCart(
                id = UUID.randomUUID(), dateTime = LocalDateTime.now(), sessionId = id,
                products = lines.map { ShoppingCartProduct(it.id, it.area.toDouble(), it.boxes, it.price, false) }
            )
            val address = input.getValue("deliveryAddress").jsonObject
            fun JsonObject.value(key: String) = this[key]?.jsonPrimitive?.contentOrNull
            val request = CreateCheckoutSessionRequest(
                sessionId = id, successUrl = requireNotNull(input.value("successUrl")), cancelUrl = requireNotNull(input.value("cancelUrl")),
                customerEmail = recipientEmail ?: input.value("customerEmail"), shippingCharge = null,
                deliveryAddress = CheckoutDeliveryAddress(
                    address.value("company"), address.value("addressLine1"), address.value("addressLine2"),
                    address.value("townOrCity"), address.value("postcode"), address.value("country")
                ),
                customerFirstName = input.value("customerFirstName")
            )
            manager.createForCheckout(
                id, request, cart,
                lines.map { CheckoutLineItem(it.id, it.product.getValue("title").jsonPrimitive.content, it.boxes, it.price,
                    imageUrl = it.product.value("image"), generalLeadTime = it.leadTime) }, null
            )
            if (paid) orders.updatePayment(id, "PAID", total.multiply(BigDecimal(100)).longValueExact(), "eur", null)
            return OrderSUT(scenarioName, id, orders, manager, catalogue)
        }
    }
}
