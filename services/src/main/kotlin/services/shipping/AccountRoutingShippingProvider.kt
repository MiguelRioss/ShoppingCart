package services.shipping

import config.ShippingMode
import config.ShippingProviderConfig
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentRateRequest
import domain.shipment.ShipmentRequest
import domain.shipment.Shipment
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
import services.shipping.rates.parseFedExRates
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger
import http.core.HttpRequestInterface
import http.core.HttpResponseInterface
import http.fedex.FedExHttpClientInterface
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Chooses the FedEx billing account from each shipment origin, including multi-origin quotes. */
class AccountRoutingShippingProvider(
    private val createProvider: (String) -> ShippingProvider = ::configuredProvider
) : ShippingProvider {
    private val logger = Logger.getLogger(javaClass.name)
    private val providers = mutableMapOf<String, ShippingProvider>()

    private fun provider(from: ShipmentAddress, to: ShipmentAddress): ShippingProvider {
        val country = when (from.countryCode.trim().uppercase()) {
            "PT" -> "PT"
            "GB", "UK" -> "GB"
            else -> throw IllegalArgumentException("Unsupported FedEx shipment origin: ${from.countryCode}")
        }
        return synchronized(providers) { providers.getOrPut(country) { createProvider(country) } }
    }

    override fun getQuotes(request: ShipmentRateRequest) = try {
        provider(request.from, request.to).getQuotes(request)
    } catch (error: Exception) {
        logger.log(Level.SEVERE, rateFailureDiagnostic(request, error), error)
        throw error
    }
    override fun prepareShipping(request: ShipmentRequest) = provider(request.from, request.to).prepareShipping(request)

    fun prepareShipping(request: ShipmentRequest, shipDate: LocalDate) : Shipment {
        val selected = provider(request.from, request.to)
        return if (selected is ShipDateAwareShippingProvider) selected.prepareShipping(request, shipDate)
        else selected.prepareShipping(request)
    }

    private fun rateFailureDiagnostic(request: ShipmentRateRequest, error: Exception): String {
        val originCountry = request.from.countryCode.trim().uppercase()
        val destinationCountry = request.to.countryCode.trim().uppercase()
        val region = when (originCountry) {
            "PT" -> "PT"
            "GB", "UK" -> "UK"
            else -> null
        }
        val config = region?.let { runCatching { ShippingProviderConfig.load(it) }.getOrNull() }
        val account = config?.account?.accountNumber?.takeLast(4)?.let { "****$it" } ?: "unavailable"

        return buildString {
            appendLine("FedEx rate request failed:")
            appendLine("  originCountry: $originCountry")
            appendLine("  destinationCountry: $destinationCountry")
            appendLine("  selectedAccountRegion: ${region ?: "unsupported"}")
            appendLine("  environment: ${config?.mode?.name ?: "unavailable"}")
            appendLine("  accountNumber: $account")
            when (error) {
                is error.FedExServiceException -> {
                    appendLine("  httpStatus: ${error.statusCode ?: "unknown"}")
                    appendLine("  sdkCode: ${error.errorCode.name} (${error.errorCode.code})")
                    appendLine("  message: ${error.message ?: "FedEx rejected the rate request"}")
                    appendLine("  responseBody:")
                    append(indentRateDetails(error.description))
                }
                else -> appendLine("  error: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}")
            }
        }.trimEnd()
    }

    private fun indentRateDetails(details: String?): String {
        if (details.isNullOrBlank()) return "    (empty response body)\n"
        val pretty = runCatching {
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            Json { prettyPrint = true; prettyPrintIndent = "  " }
                .parseToJsonElement(details).toString()
        }.getOrDefault(details)
        return pretty.lineSequence().joinToString("\n") { "    $it" }.take(8000) + "\n"
    }

    companion object {
        private fun configuredProvider(country: String): ShippingProvider {
            val config = ShippingProviderConfig.load(country)
            check(config.mode != ShippingMode.OFFLINE) { "Online FedEx account routing requires SANDBOX or PRODUCTION mode" }
            val account = requireNotNull(config.account)
            val credentials = requireNotNull(config.credentials)
            val baseUrl = if (config.mode == ShippingMode.SANDBOX)
                "https://apis-sandbox.fedex.com" else "https://apis.fedex.com"
            val httpClient = FedExHttpClient(baseUrl)
            val shipDateHttpClient = ShipDateFedExHttpClient(httpClient)
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
                FedExShipClient(FedExShipHttpClient(shipDateHttpClient)), FedExRateSelector.PreferredAccount
            )
            service.login()
            return object : ShippingProvider, ShipDateAwareShippingProvider {
                override fun getQuotes(request: ShipmentRateRequest) = service.getQuotes(request)
                override fun prepareShipping(request: ShipmentRequest) = service.prepareShipping(request)
                override fun prepareShipping(request: ShipmentRequest, shipDate: LocalDate) =
                    shipDateHttpClient.withShipDate(shipDate) { service.prepareShipping(request) }
            }
        }
    }

    private interface ShipDateAwareShippingProvider {
        fun prepareShipping(request: ShipmentRequest, shipDate: LocalDate): Shipment
    }

    /** Adds FedEx's shipDatestamp to the middleware request for scheduled label creation. */
    private class ShipDateFedExHttpClient(
        private val delegate: FedExHttpClientInterface
    ) : FedExHttpClientInterface {
        private val shipDate = ThreadLocal<LocalDate?>()

        fun <T> withShipDate(date: LocalDate, action: () -> T): T {
            shipDate.set(date)
            return try { action() } finally { shipDate.remove() }
        }

        override fun send(request: HttpRequestInterface): HttpResponseInterface {
            val date = shipDate.get() ?: return delegate.send(request)
            val root = runCatching { Json.parseToJsonElement(request.body).jsonObject }.getOrNull()
                ?: return delegate.send(request)
            val requestedShipment = root["requestedShipment"] as? JsonObject
                ?: return delegate.send(request)
            val updatedBody = JsonObject(root + ("requestedShipment" to JsonObject(
                requestedShipment + ("shipDatestamp" to JsonPrimitive(date.toString()))
            ))).toString()
            return delegate.send(object : HttpRequestInterface {
                override val method get() = request.method
                override val path get() = request.path
                override val body get() = updatedBody
                override val contentType get() = request.contentType
                override val headers get() = request.headers
            })
        }
    }
}
