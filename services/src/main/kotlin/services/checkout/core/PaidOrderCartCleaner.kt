package services.checkout.core

import db.OrderRepository
import db.ShoppingCartRepository
import kotlinx.serialization.json.*
import java.util.UUID

/** Retries are safe; only a purchased, unchanged cart is deleted. Other carts remain saved. */
class PaidOrderCartCleaner(private val orders: OrderRepository, private val carts: ShoppingCartRepository) {
    fun clear(checkoutId: String) {
        val snapshot = orders.snapshotForCheckout(checkoutId) ?: return
        val cartId = snapshot["cartId"]?.jsonPrimitive?.contentOrNull?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val cart = carts.getCartById(cartId) ?: return
        val savedAt = snapshot["cartUpdatedAt"]?.jsonPrimitive?.contentOrNull
        if (savedAt != null) {
            if (savedAt != cart.dateTime.toString()) return
        } else {
            // Older snapshots lack the timestamp; require the purchased contents to still match.
            val purchased = snapshot["cartProducts"] as? JsonArray ?: return
            if (purchased.sortedBy { it.toString() } != cartProductsSnapshot(cart).sortedBy { it.toString() }) return
        }
        carts.clearCartIfUnchanged(cart.id, cart.dateTime)
    }
}
