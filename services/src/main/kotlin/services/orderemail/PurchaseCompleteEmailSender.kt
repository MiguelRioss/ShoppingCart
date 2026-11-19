package services.orderemail

import db.OrderRepository
import kotlinx.serialization.json.JsonObject
import services.order.OrderManager
import java.util.logging.Logger

/** Sends the two automatic paid-checkout emails through the purchase-complete API route. */
class PurchaseCompleteEmailSender(
    private val mapper: PaidOrderEmailPayloadMapper,
    private val api: EmailSenderApi
) {
    private val logger = Logger.getLogger(javaClass.name)

    constructor(orders: OrderRepository, orderManager: OrderManager, api: EmailSenderApi) :
        this(PaidOrderEmailPayloadMapper(orders, orderManager), api)

    fun onPaid(checkoutId: String): JsonObject {
        logger.info("Purchase-complete payload generation started checkoutId=$checkoutId")
        val payload = mapper.fromPaidCheckout(checkoutId, setOf("invoice", "purchase-order"))
        logger.info("Purchase-complete payload generated checkoutId=$checkoutId; calling Email Sender")
        return api.startPurchaseComplete(payload)
    }
}
