
package services.checkout.core

import domain.cart.ShoppingCart
import domain.checkout.CheckoutLineItem
import domain.checkout.CheckoutShippingCharge
import domain.checkout.CreateCheckoutSessionRequest
import domain.checkout.toCheckoutLineItem
import kotlinx.serialization.json.*

/**
 * Immutable checkout-time values.
 *
 * Later changes to the cart, catalogue or delivery address
 * cannot alter the values captured for this order.
 */
fun orderSnapshot(
    request: CreateCheckoutSessionRequest,
    cart: ShoppingCart,
    productLineItems: List<CheckoutLineItem>,
    shippingCharge: CheckoutShippingCharge?
) = buildJsonObject {

    require(productLineItems.isNotEmpty()) {
        "Cannot create an order snapshot without products"
    }

    val shippingLineItem = shippingCharge?.toCheckoutLineItem()
    val items = productLineItems + listOfNotNull(shippingLineItem)

    val currency = productLineItems.first().currency.lowercase()

    require(items.all { it.currency.lowercase() == currency }) {
        "All checkout items must use the same currency"
    }

    require(items.all { it.quantity > 0 }) {
        "Checkout item quantities must be positive"
    }

    val productSubtotal =
        productLineItems.sumOf { it.amountTotal }

    val shippingTotal =
        shippingLineItem?.amountTotal
            ?: java.math.BigDecimal.ZERO

    val total = productSubtotal + shippingTotal

    // Cart information
    put("cartId", cart.id.toString())
    put("cartUpdatedAt", cart.dateTime.toString())

    // Customer information
    request.userId?.let {
        put("userId", it.toString())
    }

    request.customerEmail?.let {
        put("customerEmail", it)
    }

    request.customerFirstName?.let {
        put("customerFirstName", it)
    }
    request.customerLastName?.let {
        put("customerLastName", it)
    }

    request.customerPhone?.takeIf(String::isNotBlank)?.let {
        put("customerPhone", it)
    }


    // Financial summary
    put("currency", currency)
    put("productSubtotal", productSubtotal.toPlainString())
    put("shippingTotal", shippingTotal.toPlainString())
    put("total", total.toPlainString())

    // Checkout items, including shipping
    put("items", buildJsonArray {
        items.forEach { item ->
            add(buildJsonObject {
                put("productId", item.productId)
                put("name", item.name)
                put("quantity", item.quantity)
                put("amountTotal", item.amountTotal.toPlainString())
                put("currency", item.currency)

                item.description?.let {
                    put("description", it)
                }

                item.imageUrl?.let {
                    put("imageUrl", it)
                }

                item.generalLeadTime?.let {
                    put("generalLeadTime", it)
                }
            })
        }
    })

    // Shipping information
    shippingCharge?.let { shipping ->
        put("shipping", buildJsonObject {

            shipping.serviceType?.let {
                put("serviceType", it)
            }

            shipping.serviceName?.let {
                put("serviceName", it)
            }

            put(
                "amountTotal",
                shippingTotal.toPlainString()
            )

            put("currency", shippingLineItem!!.currency)

            shipping.estimatedDeliveryDate?.let {
                put("estimatedDeliveryDate", it)
            }

            shipping.originalAmount?.let {
                put("originalAmount", it.toPlainString())
            }

            shipping.originalCurrency?.let {
                put("originalCurrency", it)
            }
            if (shipping.originServiceTypes.isNotEmpty()) {
                put("originServiceTypes", JsonArray(shipping.originServiceTypes.map(::JsonPrimitive)))
            }
        })
    }

    // Delivery address
    request.deliveryAddress?.let { address ->
        put("deliveryAddress", buildJsonObject {

            address.company?.let {
                put("company", it)
            }

            address.addressLine1?.let {
                put("addressLine1", it)
            }

            address.addressLine2?.let {
                put("addressLine2", it)
            }

            address.townOrCity?.let {
                put("townOrCity", it)
            }

            address.postcode?.let {
                put("postcode", it)
            }

            address.country?.let {
                put("country", it)
            }
        })
    }

    // Original cart product configuration
    put("cartProducts", cartProductsSnapshot(cart))
}

/**
 * Preserves the product configuration selected in the cart.
 */
internal fun cartProductsSnapshot(
    cart: ShoppingCart
) = buildJsonArray {

    cart.products.forEach { product ->
        add(buildJsonObject {

            put("productId", product.productId)
            put("isSample", product.isSample)

            product.squareMeters?.let {
                put("squareMeters", it)
            }

            product.amountBoxes?.let {
                put("amountBoxes", it)
            }

            product.sampleUnits?.let {
                put("sampleUnits", it)
            }

            put(
                "totalPricePerProduct",
                product.totalPricePerProduct.toPlainString()
            )
        })
    }
}
