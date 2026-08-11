package dto

enum class CustomerType {
    PrivateCustomer,
    Business;

    companion object {
        fun from(value: String?): CustomerType? =
            when (value?.trim()?.lowercase()) {
                "privatecustomer", "private_customer", "private customer", "private" -> PrivateCustomer
                "business" -> Business
                else -> null
            }
    }
}
