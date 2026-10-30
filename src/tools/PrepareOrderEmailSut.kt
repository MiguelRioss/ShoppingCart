package tools

import db.postgres.Database
import db.postgres.PostgresOrderRepository
import db.postgres.PostgresUserRepository
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductCatalogDataSource
import services.orderemail.OrderEmailPayloadBuilder
import services.orderemail.PaymentConfirmationPayload
import java.nio.file.Files
import java.nio.file.Path

/** Read-only preparation of a real-order request. Never updates an order or sends an email. */
fun main(args: Array<String>) {
    require(args.size >= 2) { "Supply order UUID and output JSON path" }
    val database = requireNotNull(Database.fromEnvironment()) { "DATABASE_URL is required" }
    database.use { db ->
        val orders = PostgresOrderRepository(db)
        val source = requireNotNull(orders.findForEmail(args[0])) { "Order not found" }
        require(source["status"]?.jsonPrimitive?.content == "PAID") { "A paid saved order is required" }
        val checkout = source.getValue("checkoutSessionId").jsonPrimitive.content
        val order = requireNotNull(orders.findByCheckoutForEmail(checkout))
        val catalogue = ProductCatalogDataSource()
        val snapshot = order.getValue("snapshot").jsonObject
        val provenance = mutableListOf<String>()
        val items = snapshot.getValue("items").jsonArray.map { value ->
            val item = value.jsonObject
            val id = item.getValue("productId").jsonPrimitive.long
            if (id == 0L) return@map item
            val product = Json.parseToJsonElement(requireNotNull(catalogue.getProductById(id))).jsonObject
            val purchase = product["purchase_information"] as? JsonObject
            val lead = (purchase?.get("lead_times_and_quotation") as? JsonObject)?.get("general_lead_time")
            val extra = buildJsonObject {
                if ((item["imageUrl"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) {
                    put("imageUrl", product.getValue("image")); provenance += "Product $id image: current catalogue"
                }
                if ((item["generalLeadTime"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank() && lead != null) {
                    put("generalLeadTime", lead); provenance += "Product $id lead time: current catalogue"
                }
            }
            JsonObject(item + extra)
        }
        // Enrich only the local preview; preserve stored names, quantities, prices, amounts and customer.
        val enriched = JsonObject(order + ("snapshot" to JsonObject(snapshot + ("items" to JsonArray(items)))))
        val payload = PaymentConfirmationPayload(OrderEmailPayloadBuilder(PostgresUserRepository(db), catalogue)).prepare(enriched)
        val revision = args.getOrNull(2) ?: "real-data-footer-v1"
        val request = JsonObject(payload + mapOf(
            "idempotencyKey" to JsonPrimitive("sut:${args[0]}:payment-confirmation:$revision"),
            "eventId" to JsonPrimitive("sut:${args[0]}:$revision")
        ))
        val path = Path.of(args[1]).toAbsolutePath()
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), request))
        Files.writeString(path.resolveSibling(path.fileName.toString() + ".sources.txt"),
            "Saved paid order: ${args[0]}\nNames, quantities, prices, paid amount and customer: saved order/account\n" +
            provenance.joinToString("\n") + "\nSUT preview event only. No order data changed. No email sent by this tool.\n")
        println("Real-order SUT request prepared: $path")
    }
}
