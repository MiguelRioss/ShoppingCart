package services.shipping.fedex

import domain.shipping.ShippingProvider
import domain.shipping.ShippingProviderType
import domain.shipping.ShippingQuotation
import productdatabaseaccesslayer.ProductDataAccess
import services.shipping.ProductShippingReader
import services.shipping.fedex.ratequotation.FedExQuoteClient
import services.shipping.fedex.ratequotation.FedExQuoteRequest

class FedExShippingProvider(
    private val productDataAccess: ProductDataAccess,
    private val fedExQuoteClient: FedExQuoteClient,
    private val manualFallbackQuote: ManualFedExFallbackQuote = ManualFedExFallbackQuote()
) : ShippingProvider {

    override val type: ShippingProviderType =
        ShippingProviderType.FEDEX

    override fun quote(quotation: ShippingQuotation): ShippingQuotation {
        val productShippingReader = ProductShippingReader(productDataAccess)
        val packingSize = productShippingReader.packageSizeFor(quotation)
        val originAddress = productShippingReader.collectionAddressFor(quotation.productId)

        val fedExRequest =
            FedExQuoteRequest(
                originAddress = originAddress,
                destinationAddress = quotation.destinationAddress,
                packageSize = packingSize,
                incoterm = quotation.incoterm
            )

        val options =
            runCatching {
                fedExQuoteClient.quote(fedExRequest)
            }.getOrElse { error ->
                manualFallbackQuote.optionsFor(quotation)
                    .ifEmpty {
                        throw error
                    }
            }

        return quotation.copy(
            options = options
        )
    }
}
