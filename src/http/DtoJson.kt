import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun JsonObjectBuilder.putNullable(name: String, value: String?) {
    put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

 fun JsonObject.stringValueIncludingBlank(name: String): String? =
    this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.contentOrNull

 fun JsonObject.objectValueIncludingNull(name: String): JsonObject? =
    this[name]?.takeUnless { it is JsonNull }?.jsonObject

 fun JsonObject.booleanValue(name: String): Boolean? =
    this[name]?.jsonPrimitive?.booleanOrNull
