package dto

/**
 * Supported customer categories for registration.
 */
enum class CustomerType {
    PrivateCustomer,
    Business;

    companion object {
        /**
         * Converts user input into a [CustomerType].
         *
         * @param value raw customer type text from a request body
         * @return matching customer type, or null when the value is unknown/missing
         */
        fun from(value: String?): CustomerType? =
            when (value?.trim()?.lowercase()) {
                "privatecustomer", "private_customer", "private customer", "private" -> PrivateCustomer
                "business" -> Business
                else -> null
            }
    }
}
