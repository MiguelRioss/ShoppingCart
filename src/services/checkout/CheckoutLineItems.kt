package services.checkout

import domain.checkout.CheckoutLineItem
import domain.checkout.CheckoutShippingCharge
import domain.cart.ShoppingCartProduct

internal fun ShoppingCartProduct.toCheckoutLineItem(checkoutProduct: CheckoutProduct): CheckoutLineItem =
    CheckoutLineItem(
        productId = productId,
        name = checkoutProduct.name,
        quantity = amountBoxes,
        amountTotal = totalPricePerProduct,
        imageUrl = checkoutProduct.imageUrl
    )

internal fun CheckoutShippingCharge.toCheckoutLineItem(): CheckoutLineItem =
    CheckoutLineItem(
        productId = 0,
        name = serviceName?.takeIf { it.isNotBlank() } ?: "Shipping",
        quantity = 1,
        amountTotal = amountTotal,
        currency = currency.lowercase(),
        description = null
    )


