/**
 * Typed JSON helper functions for reading product and request fields safely.
 */
package support

import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

fun JsonObject.objectValue(name: String): JsonObject =
    this[name]?.jsonObject ?: error("$name is missing")

fun JsonObject.objectValueOrNull(name: String): JsonObject? =
    this[name] as? JsonObject

fun JsonObject.stringValue(name: String): String? =
    this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

fun JsonObject.longValue(name: String): Long? =
    this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.longOrNull

fun JsonObject.decimalValue(name: String): BigDecimal =
    requireNotNull(optionalDecimalValue(name)) { "$name is missing" }

fun JsonObject.optionalDecimalValue(name: String): BigDecimal? =
    stringValue(name)?.let { runCatching { BigDecimal(it) }.getOrNull() }

fun JsonObject.stringListValue(name: String): List<String> {
    val value = this[name] ?: return emptyList()

    return when (value) {
        is JsonArray -> value.mapNotNull {
            it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
        }
        else -> value.jsonPrimitive.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let(::listOf)
            ?: emptyList()
    }
}

fun JsonElement.decimalValueOrNull(): BigDecimal? {
    jsonObjectOrNull()
        ?.get("amount")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.let { value ->
            return value.toBigDecimalOrNull()
        }

    return runCatching {
        jsonPrimitive.contentOrNull?.toBigDecimalOrNull()
    }.getOrNull()
}

fun JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching {
        jsonObject
    }.getOrNull()

