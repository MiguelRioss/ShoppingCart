package domain.checkout

/** Provider-neutral lifecycle state for a hosted checkout session. */
enum class CheckoutSessionStatus(
    val responseValue: String
) {
    CREATED("created"),
    PENDING("pending"),
    FAILED("failed");

    companion object {
        fun fromProviderStatus(status: String): CheckoutSessionStatus =
            when (status.lowercase()) {
                "open", "complete", "created" -> CREATED
                "expired", "failed" -> FAILED
                else -> PENDING
            }
    }
}
