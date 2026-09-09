package services.shipping.fedex.ratequotation

import domain.shipping.ShippingAddress
import java.math.BigDecimal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class FedExRateRequestMapper(
    private val accountNumber: String
) {

    fun map(
        request: FedExQuoteRequest
    ): JsonObject =
        buildJsonObject {
            putJsonObject("accountNumber") {
                put("value", accountNumber)
            }

            putJsonObject(
                "rateRequestControlParameters"
            ) {
                put(
                    "returnTransitTimes",
                    request.returnTransitTimes
                )
            }

            putJsonObject("requestedShipment") {
                putJsonObject("shipper") {
                    put(
                        "address",
                        addressJson(
                            address = request.originAddress,
                            postalCodeOnly = request.postalCodeOnlyAddresses
                        )
                    )
                }

                putJsonObject("recipient") {
                    put(
                        "address",
                        addressJson(
                            address = request.destinationAddress,
                            postalCodeOnly = request.postalCodeOnlyAddresses
                        )
                    )
                }

                put(
                    "pickupType",
                    request.pickupType
                )

                request.shipDate?.let {
                    put(
                        "shipDateStamp",
                        it.toString()
                    )
                }

                request.serviceType?.let {
                    put(
                        "serviceType",
                        it
                    )
                }

                request.packagingType?.let {
                    put(
                        "packagingType",
                        it
                    )
                }

                putJsonArray(
                    "rateRequestType"
                ) {
                    request.rateRequestTypes.forEach {
                        add(it)
                    }
                }

                request.customsDetail?.let { customs ->
                    putJsonObject(
                        "customsClearanceDetail"
                    ) {
                        putJsonObject(
                            "commercialInvoice"
                        ) {
                            put(
                                "purpose",
                                "SOLD"
                            )
                        }

                        putJsonObject(
                            "totalCustomsValue"
                        ) {
                            put(
                                "amount",
                                customs.customsValue
                            )

                            put(
                                "currency",
                                customs.currency
                            )
                        }

                        putJsonArray(
                            "commodities"
                        ) {
                            add(
                                buildJsonObject {
                                    put(
                                        "description",
                                        customs.commodityDescription
                                    )

                                    put(
                                        "countryOfManufacture",
                                        customs
                                            .countryOfManufacture
                                            .toFedExCountryCode()
                                    )

                                    put(
                                        "harmonizedCode",
                                        customs.harmonizedCode
                                    )

                                    put(
                                        "quantity",
                                        customs.quantity
                                    )

                                    put(
                                        "quantityUnits",
                                        customs.quantityUnits
                                    )

                                    putJsonObject(
                                        "unitPrice"
                                    ) {
                                        put(
                                            "amount",
                                            customs.unitPrice
                                        )

                                        put(
                                            "currency",
                                            customs.currency
                                        )
                                    }

                                    putJsonObject(
                                        "customsValue"
                                    ) {
                                        put(
                                            "amount",
                                            customs.customsValue
                                        )

                                        put(
                                            "currency",
                                            customs.currency
                                        )
                                    }

                                    putJsonObject(
                                        "weight"
                                    ) {
                                        put(
                                            "units",
                                            "KG"
                                        )

                                        put(
                                            "value",
                                            request
                                                .packageSize
                                                .packedWeight
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                putJsonArray(
                    "requestedPackageLineItems"
                ) {
                    add(
                        buildJsonObject {
                            putJsonObject(
                                "weight"
                            ) {
                                put(
                                    "units",
                                    "KG"
                                )

                                put(
                                    "value",
                                    request
                                        .packageSize
                                        .packedWeight
                                )
                            }

                            if (request.includeDimensions) {
                                putJsonObject(
                                    "dimensions"
                                ) {
                                    put(
                                        "length",
                                        request
                                            .packageSize
                                            .length
                                            .toFedExDimension()
                                    )

                                    put(
                                        "width",
                                        request
                                            .packageSize
                                            .width
                                            .toFedExDimension()
                                    )

                                    put(
                                        "height",
                                        request
                                            .packageSize
                                            .height
                                            .toFedExDimension()
                                    )

                                    put(
                                        "units",
                                        "CM"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

    private fun addressJson(
        address: ShippingAddress,
        postalCodeOnly: Boolean = false
    ): JsonObject =
        buildJsonObject {
            if (!postalCodeOnly) {
                putJsonArray(
                    "streetLines"
                ) {
                    address.streetLines.forEach {
                        add(it)
                    }
                }

                put(
                    "city",
                    address.city
                )
            }

            put(
                "postalCode",
                address.postalCode
            )

            put(
                "countryCode",
                address
                    .countryCode
                    .toFedExCountryCode()
            )
        }

    private fun BigDecimal
            .toFedExDimension(): Int =
        toInt()

    private fun String
            .toFedExCountryCode(): String {
        val country = trim().uppercase()

        return when (country) {
            "PORTUGAL",
            "PT" ->
                "PT"

            "UNITED KINGDOM",
            "UK",
            "GB" ->
                "GB"

            "SPAIN",
            "ES" ->
                "ES"

            "FRANCE",
            "FR" ->
                "FR"

            "ITALY",
            "IT" ->
                "IT"

            "GERMANY",
            "DE" ->
                "DE"

            "SWITZERLAND",
            "CH" ->
                "CH"

            "NORWAY",
            "NO" ->
                "NO"

            "UNITED STATES",
            "USA",
            "US" ->
                "US"

            else ->
                country.takeIf { it.matches(Regex("[A-Z]{2}")) }
                    ?: error(
                        "Unsupported FedEx country: $this"
                    )
        }
    }
}
