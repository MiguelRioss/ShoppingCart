package tools

import db.postgres.Database
import db.postgres.PostgresOrderRepository
import db.postgres.PostgresUserRepository
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.orderemail.*
import java.nio.file.Files
import java.nio.file.Path

/** Prepares only the agreed invoice/email templates from actual supplied document data. */
fun main(args: Array<String>) {
    require(args.size >= 2) { "Supply order UUID and output JSON path" }
    val database = requireNotNull(Database.fromEnvironment()) { "DATABASE_URL is required" }
    database.use { db ->
        val orders = PostgresOrderRepository(db)
        val order = requireNotNull(orders.findForEmail(args[0])) { "Order not found" }
        val snapshot = order.getValue("snapshot").jsonObject
        val data = args.getOrNull(3)?.takeIf { it.isNotBlank() }?.let {
            Json.parseToJsonElement(Files.readString(Path.of(it))).jsonObject
        } ?: snapshot["emailDocumentData"] as? JsonObject
            ?: error("Approved invoice document data is required. Supply -PdocumentData=path.json; no replacement invoice will be generated.")
        val context = args.getOrNull(4)?.takeIf { it.isNotBlank() }?.let {
            Json.parseToJsonElement(Files.readString(Path.of(it))).jsonObject
        } ?: snapshot["emailContext"] as? JsonObject ?: JsonObject(emptyMap())
        val builder = OrderEmailPayloadBuilder(PostgresUserRepository(db), ProductCatalogDataSource())
        val emails = SendOrderEmailService(orders, builder, EmailSenderClient { error("Preparation must never send") })
        val payload = emails.prepare(args[0], OrderEmailTemplate.ORDER_CONFIRMATION, context, OrderDocumentDataParser.parse(data))
        val revision = args.getOrNull(2) ?: "approved-invoice-v1"
        val request = JsonObject(payload + mapOf(
            "idempotencyKey" to JsonPrimitive("sut:${args[0]}:order-confirmation:$revision"),
            "eventId" to JsonPrimitive("sut:${args[0]}:$revision")
        ))
        val path = Path.of(args[1]).toAbsolutePath()
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), request))
        println("Approved-template SUT request prepared: $path")
    }
}
