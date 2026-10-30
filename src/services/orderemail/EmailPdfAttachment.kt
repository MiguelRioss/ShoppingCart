package services.orderemail

import kotlinx.serialization.json.*
import java.util.Base64

/** In-memory PDF produced by ShoppingCart, never a filesystem path or remote URL. */
data class EmailPdfAttachment(val documentId: String, val type: String, val filename: String, val bytes: ByteArray) {
    fun toJson(): JsonObject {
        require(bytes.size >= 5 && bytes.copyOfRange(0,5).toString(Charsets.US_ASCII) == "%PDF-") { "Generator did not return PDF bytes" }
        require(bytes.size <= 8 * 1024 * 1024) { "PDF exceeds the 8 MiB attachment limit" }
        return buildJsonObject {
            put("documentId", documentId); put("type", type); put("filename", filename)
            put("contentType", "application/pdf"); put("contentBase64", Base64.getEncoder().encodeToString(bytes))
        }
    }
}
