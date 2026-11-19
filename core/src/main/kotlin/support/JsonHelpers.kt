/**
 * Typed JSON helper functions for reading product and request fields safely.
 */
package support

import java.math.BigDecimal
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive


fun JsonObject.stringValue(name: String): String? =
    this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }



fun JsonObject.optionalDecimalValue(name: String): BigDecimal? =
    stringValue(name)?.let { runCatching { BigDecimal(it) }.getOrNull() }



