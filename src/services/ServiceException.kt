package services

enum class ServiceErrorCode(
    val code: Int,
    val defaultMessage: String,
    val defaultDescription: String = defaultMessage
) {
    ProductDoesNotExist(
        code = 1000,
        defaultMessage = "Product does not exist",
        defaultDescription = "The requested product was not found in the catalog"
    ),
    ProductPurchaseInformationMissing(
        code = 1001,
        defaultMessage = "Product purchase information is missing",
        defaultDescription = "The product cannot be added to the cart because purchase_information is missing"
    ),
    ProductOrderInformationMissing(
        code = 1002,
        defaultMessage = "Product order information is missing",
        defaultDescription = "The product cannot be added to the cart because purchase_information.order is missing"
    ),
    ProductM2PerBoxMissing(
        code = 1003,
        defaultMessage = "Product m2 per box is missing",
        defaultDescription = "The product cannot be added to the cart because m2_per_box is missing or invalid"
    ),
    ProductClientPricePerM2Missing(
        code = 1004,
        defaultMessage = "Product client price per m2 is missing",
        defaultDescription = "The product cannot be added to the cart because client_price_per_m2 is missing or invalid"
    ),
    CartNotFound(
        code = 2000,
        defaultMessage = "Shopping cart not found",
        defaultDescription = "No shopping cart exists for the provided sessionId"
    ),
    CartIsEmpty(
        code = 2001,
        defaultMessage = "Shopping cart is empty",
        defaultDescription = "The shopping cart must contain at least one product before checkout"
    ),
    CheckoutCreationFailed(
        code = 2002,
        defaultMessage = "Checkout creation failed",
        defaultDescription = "The payment provider could not create a checkout session"
    )
}

class ServiceException(
    val errorCode: ServiceErrorCode,
    override val message: String = errorCode.defaultMessage,
    val description: String = errorCode.defaultDescription
) : IllegalArgumentException(message)
