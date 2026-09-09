package services.shipping.fedex

import domain.shipping.ShippingAddress
import domain.shipping.ShippingPackageSize

class FedExShipmentMapper : FedExShipmentMapperInterface {

    override fun map(
        originAddress: ShippingAddress,
        destinationAddress: ShippingAddress,
        packageSize: ShippingPackageSize
    ): FedExMappedShipment =
        FedExMappedShipment(
            originAddress = originAddress.toFedExAddress(),
            destinationAddress = destinationAddress.toFedExAddress(),
            packageData = FedExPackageData(
                length = packageSize.length.intValueExact(),
                width = packageSize.width.intValueExact(),
                height = packageSize.height.intValueExact(),
                weightKg = packageSize.packedWeight.toDouble()
            )
        )

    private fun ShippingAddress.toFedExAddress() =
        FedExAddress(
            streetLines = streetLines,
            city = city,
            postalCode = postalCode,
            countryCode = countryCode.toFedExCountryCode()
        )

    private fun String.toFedExCountryCode(): String {
        val country = trim().uppercase()

        return when (country) {
            "PORTUGAL", "PT" -> "PT"
            "UNITED KINGDOM", "UK", "GB" -> "GB"
            "SPAIN", "ES" -> "ES"
            "FRANCE", "FR" -> "FR"
            "ITALY", "IT" -> "IT"
            "GERMANY", "DE" -> "DE"
            "SWITZERLAND", "CH" -> "CH"
            "NORWAY", "NO" -> "NO"
            "UNITED STATES", "USA", "US" -> "US"
            else -> country.takeIf { it.matches(Regex("[A-Z]{2}")) }
                ?: error("Unsupported FedEx country: $this")
        }
    }
}