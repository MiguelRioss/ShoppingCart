package services.shipping

import config.ShippingMode
import config.ShippingProviderConfig
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentRateRequest
import domain.shipment.ShipmentRequest
import shipment.core.ShippingProvider
import shipment.core.ShipmentProviderType

/** Chooses the billing account for each origin/destination pair, including each multi-origin quote. */
class AccountRoutingShippingProvider(
    private val createProvider: (String) -> ShippingProvider = ::configuredProvider
) : ShippingProvider {
    private val providers = mutableMapOf<String, ShippingProvider>()

    private fun provider(from: ShipmentAddress, to: ShipmentAddress): ShippingProvider {
        val country = if (isUk(from.countryCode) || isUk(to.countryCode)) "GB" else "PT"
        return synchronized(providers) { providers.getOrPut(country) { createProvider(country) } }
    }

    override fun getQuotes(request: ShipmentRateRequest) = provider(request.from, request.to).getQuotes(request)
    override fun prepareShipping(request: ShipmentRequest) = provider(request.from, request.to).prepareShipping(request)

    companion object {
        private fun isUk(country: String) = country.trim().uppercase() in setOf("GB", "UK")

        private fun configuredProvider(country: String): ShippingProvider {
            val config = ShippingProviderConfig.load(country)
            check(config.mode != ShippingMode.OFFLINE) { "Online FedEx account routing requires SANDBOX or PRODUCTION mode" }
            return ShippingProvider.login(
                type = ShipmentProviderType.FEDEX,
                account = requireNotNull(config.account),
                credentials = requireNotNull(config.credentials),
                environment = requireNotNull(config.providerEnvironment)
            )
        }
    }
}
