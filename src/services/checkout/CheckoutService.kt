package services.checkout

import domain.checkout.Checkout
import domain.checkout.CheckoutSession

interface CheckoutService {
    fun createCheckout(
        checkout: Checkout
    ): CheckoutSession
}