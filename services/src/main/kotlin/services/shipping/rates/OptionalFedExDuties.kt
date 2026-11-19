package services.shipping.rates

import dto.fedex.rate.toFedExRateResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal fun parseFedExRates(body: String, origin: String, destination: String) =
    normalizeOptionalDuties(body, origin, destination).toFedExRateResponse()

internal fun normalizeOptionalDuties(body: String, origin: String, destination: String): String {
    val domestic = origin.equals(destination, ignoreCase = true)
    val intraEu = origin.uppercase() in EU_COUNTRIES && destination.uppercase() in EU_COUNTRIES
    if (!domestic && !intraEu) return body

    val root = Json.parseToJsonElement(body).jsonObject
    val output = root["output"]?.jsonObject ?: return body
    val replies = output["rateReplyDetails"]?.jsonArray ?: return body
    val updatedReplies = JsonArray(replies.map { reply ->
        val replyObject = reply.jsonObject
        val rated = replyObject["ratedShipmentDetails"]?.jsonArray ?: return@map reply
        JsonObject(replyObject + ("ratedShipmentDetails" to JsonArray(rated.map { item ->
            val itemObject = item.jsonObject
            if (itemObject.containsKey("totalDutiesAndTaxes")) return@map item
            JsonObject(itemObject + ("totalDutiesAndTaxes" to JsonPrimitive(0)))
        })))
    })
    return JsonObject(root + ("output" to JsonObject(output + ("rateReplyDetails" to updatedReplies)))).toString()
}

private val EU_COUNTRIES = setOf(
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
    "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE"
)
