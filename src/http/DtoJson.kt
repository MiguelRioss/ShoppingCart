import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Writes a nullable string field as either a JSON primitive or explicit null. */
fun JsonObjectBuilder.putNullable(name: String, value: String?) {
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

/** Reads a nullable string without treating a blank value as absent. */
 fun JsonObject.stringValueIncludingBlank(name: String): String? =
    this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.contentOrNull

/** Reads a nested object while accepting a missing or explicit-null field. */
 fun JsonObject.objectValueIncludingNull(name: String): JsonObject? =
    this[name]?.takeUnless { it is JsonNull }?.jsonObject

/** Reads an optional boolean primitive. */
 fun JsonObject.booleanValue(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull
