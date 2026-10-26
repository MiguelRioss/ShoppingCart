package services.checkout.core

import domain.cart.ShoppingCart
import domain.checkout.CheckoutLineItem
import domain.checkout.CreateCheckoutSessionRequest
import kotlinx.serialization.json.*

/** Immutable checkout-time values: later cart, catalog and address edits cannot alter an order. */
internal fun orderSnapshot(request: CreateCheckoutSessionRequest, cart: ShoppingCart, items: List<CheckoutLineItem>) = buildJsonObject {
    put("cartId", cart.id.toString())
    request.customerEmail?.let { put("customerEmail", it) }
    put("currency", items.first().currency)
    put("total", items.sumOf { it.amountTotal }.toPlainString())
    put("items", buildJsonArray { items.forEach { item -> add(buildJsonObject {
        put("productId", item.productId); put("name", item.name); put("quantity", item.quantity)
        put("amountTotal", item.amountTotal.toPlainString()); put("currency", item.currency)
        item.description?.let { put("description", it) }
    }) } })
    request.deliveryAddress?.let { address -> put("deliveryAddress", buildJsonObject {
        address.company?.let { put("company", it) }; address.addressLine1?.let { put("addressLine1", it) }
        address.addressLine2?.let { put("addressLine2", it) }; address.townOrCity?.let { put("townOrCity", it) }
        address.postcode?.let { put("postcode", it) }; address.country?.let { put("country", it) }
    }) }
    put("cartProducts", buildJsonArray { cart.products.forEach { product -> add(buildJsonObject {
        put("productId", product.productId); put("isSample", product.isSample)
        product.squareMeters?.let { put("squareMeters", it) }
        product.amountBoxes?.let { put("amountBoxes", it) }
        product.sampleUnits?.let { put("sampleUnits", it) }
        put("totalPricePerProduct", product.totalPricePerProduct.toPlainString())
    }) } })
}
