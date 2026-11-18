package services.shipping

import config.ShippingMode
import config.ShippingProviderConfig
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentRateRequest
import domain.shipment.ShipmentRequest
import shipment.core.ShippingProvider
import auth.fedex.FedExAuthClient
import auth.fedex.FedExAuthHttpClient
import auth.fedex.FedExAuthTokenManager
import domain.auth.FedExCredentials
import dto.fedex.rate.FedExRateRequest
import http.fedex.FedExHttpClient
import shipment.fedex.FedExShipmentProviderService
import shipment.fedex.rate.FedExRateHttpClient
import shipment.fedex.rate.FedExRateSelector
import shipment.fedex.rate.FedExRateService
import shipment.fedex.ship.FedExShipClient
import shipment.fedex.ship.FedExShipHttpClient

/** Chooses the FedEx billing account from each shipment origin, including multi-origin quotes. */
class AccountRoutingShippingProvider(
    private val createProvider: (String) -> ShippingProvider = ::configuredProvider
) : ShippingProvider {
    private val providers = mutableMapOf<String, ShippingProvider>()

    private fun provider(from: ShipmentAddress, to: ShipmentAddress): ShippingProvider {
        val country = when (from.countryCode.trim().uppercase()) {
            "PT" -> "PT"
            "GB", "UK" -> "GB"
            else -> throw IllegalArgumentException("Unsupported FedEx shipment origin: ${from.countryCode}")
        }
        return synchronized(providers) { providers.getOrPut(country) { createProvider(country) } }
    }

    override fun getQuotes(request: ShipmentRateRequest) = provider(request.from, request.to).getQuotes(request)
    override fun prepareShipping(request: ShipmentRequest) = provider(request.from, request.to).prepareShipping(request)

    companion object {
        private fun configuredProvider(country: String): ShippingProvider {
            val config = ShippingProviderConfig.load(country)
            check(config.mode != ShippingMode.OFFLINE) { "Online FedEx account routing requires SANDBOX or PRODUCTION mode" }
            val account = requireNotNull(config.account)
            val credentials = requireNotNull(config.credentials)
            val baseUrl = if (config.mode == ShippingMode.SANDBOX)
                "https://apis-sandbox.fedex.com" else "https://apis.fedex.com"
            val httpClient = FedExHttpClient(baseUrl)
            val auth = FedExAuthTokenManager(
                FedExAuthClient(FedExAuthHttpClient(httpClient)),
                FedExCredentials(credentials.clientId, credentials.clientSecret)
            )
            val rateHttp = FedExRateHttpClient(httpClient)
            val rateService = object : FedExRateService {
                override fun getRates(request: FedExRateRequest, accessToken: String) =
                    parseFedExRates(rateHttp.getRates(request, accessToken).body,
                        request.shipper.countryCode,
                        request.recipient.countryCode)
            }
            val service = FedExShipmentProviderService(
                account, credentials, auth, rateService,
                FedExShipClient(FedExShipHttpClient(httpClient)), FedExRateSelector.PreferredAccount
            )
            service.login()
            return object : ShippingProvider {
                override fun getQuotes(request: ShipmentRateRequest) = service.getQuotes(request)
                override fun prepareShipping(request: ShipmentRequest) = service.prepareShipping(request)
            }
        }
    }
}
