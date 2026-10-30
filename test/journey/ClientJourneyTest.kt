package journey

import com.sun.net.httpserver.HttpServer
import db.offline.*
import domain.checkout.CheckoutShippingCharge
import http.*
import http.auth.*
import http.cart.*
import http.checkout.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import productdatabaseaccesslayer.ProductDataAccess
import services.auth.*
import services.cart.*
import services.checkout.core.*
import services.checkout.payment.stripe.*
import services.orderemail.*
import services.user.UserManager
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as NetworkRequest
import java.net.http.HttpResponse as NetworkResponse
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/** Real local HTTP across both applications, fake external Stripe and SMTP, optional real Neon history. */
@Tag("journey")
class ClientJourneyTest {
    @Test fun customerCheckoutPaymentAndEmailJourney() {
        val runId = "journey-${UUID.randomUUID()}"
        val key = UUID.randomUUID().toString()
        val emailRepo = Path.of(System.getenv("EMAIL_SENDER_REPO") ?: "../EMAIL_SENDER").toAbsolutePath().normalize()
        require(emailRepo.resolve("EMAIL_SENDER_v2/checks/journey-server.mjs").toFile().isFile) {
            "Set EMAIL_SENDER_REPO to the EMAIL_SENDER checkout containing the journey harness"
        }
        val args = mutableListOf("node")
        if (System.getenv("JOURNEY_USE_NEON") == "true" && emailRepo.resolve(".env").toFile().isFile) args += "--env-file=${emailRepo.resolve(".env")}"
        args += listOf(emailRepo.resolve("EMAIL_SENDER_v2/checks/journey-server.mjs").toString(), runId, key)
        val emailProcess = ProcessBuilder(args).directory(emailRepo.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        var emailBase: String? = null
        val stripe = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val app = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        fun call(base: String, path: String, method: String = "GET", body: String? = null, token: String? = null, signature: String? = null): NetworkResponse<String> {
            val request = NetworkRequest.newBuilder(URI(base + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
            token?.let { request.header("Authorization", "Bearer $it") }
            signature?.let { request.header("Stripe-Signature", it) }
            return client.send(request.method(method, body?.let(NetworkRequest.BodyPublishers::ofString) ?: NetworkRequest.BodyPublishers.noBody()).build(), NetworkResponse.BodyHandlers.ofString())
        }
        fun NetworkResponse<String>.expect(status: Int): JsonObject {
            assertEquals(status, statusCode(), body())
            return Json.parseToJsonElement(body()).jsonObject
        }
        try {
            val line = CompletableFuture.supplyAsync { emailProcess.inputStream.bufferedReader().readLine() }.get(20, TimeUnit.SECONDS)
            require(line?.startsWith("JOURNEY_PORT=") == true) { "EMAIL_SENDER test server failed to start" }
            emailBase = "http://127.0.0.1:${line.substringAfter('=')}"
            val emailUrl = emailBase
            var sequence = 0
            stripe.createContext("/checkout") { exchange ->
                val form = exchange.requestBody.bufferedReader().readText()
                assertTrue(form.contains("line_items"))
                val id = "$runId:${++sequence}"
                val response = """{"id":"$id","status":"open","url":"https://example.com/fake-checkout"}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            stripe.start()
            val users = InMemoryUserRepository()
            val carts = InMemoryShoppingCartRepository()
            val orders = InMemoryOrderRepository()
            var leadTime = "2-4 Weeks"
            val catalogue = object : ProductDataAccess {
                override fun getAllProducts() = "[]"
                override fun getPurchasableProducts() = "[]"
                override fun getProductBySlug(productSlug: String) = "{}"
                override fun getProductById(productId: Long) = """{"id":1,"title":"Journey tile","for_sale":true,"image":"https://example.com/tile.png","general_lead_time":"$leadTime","supplier":{"id":"journey-supplier","contact_name":"Journey supplier","email":"supplier@example.com","supplier_product_id":"JOURNEY-TILE"},"purchase_information":{"order":{"m2_per_box":"0.5","client_price_per_m2":"90"}}}"""
            }
            val cartService = ShoppingCartManager(carts, catalogue)
            val auth = AuthManager(users, InMemoryAuthTokenRepository())
            val admins = mutableSetOf<UUID>()
            val emailBuilder = OrderEmailPayloadBuilder(users, catalogue)
            val senderClient = HttpEmailSenderClient({ emailUrl }, { key })
            val confirmation = AutomaticPaymentEmailService(orders, PaymentConfirmationPayload(OrderEmailPayloadBuilder(users, catalogue))::prepare,
                HttpEmailSenderClient({ emailUrl }, { key }))
            val checkout = CheckoutManager(cartService, StripePaymentProvider(StripeConfig("sk_test_fake", "http://127.0.0.1:${stripe.address.port}/checkout")),
                object : services.shipping.ShippingChargeProvider {
                    override fun calculateShipping(cart: domain.cart.ShoppingCart, destination: domain.shipment.ShipmentAddress) = CheckoutShippingCharge("STANDARD", "Shipping", BigDecimal("25.00"), "eur")
                }, PurchasableProductReader(catalogue), orders)
            val module = HttpModule(listOf(
                Route("POST", "/admin/orders/:orderId/send-email", http.admin.SendOrderEmailHandler(auth, SendOrderEmailService(orders, emailBuilder, senderClient), admins)),
                Route("POST", "/register", RegisterHandler(UserManager(users), auth, cartService)),
                Route("POST", "/login", LoginHandler(LoginService(auth, cartService))),
                Route("POST", "/cart", SaveCartHandler(cartService, auth)),
                Route("GET", "/cart", GetCartHandler(auth, cartService)),
                Route("POST", "/checkout", CreateCheckoutHandler(checkout, auth)),
                Route("GET", "/orders", GetOrdersHandler(orders, auth)),
                Route("POST", "/webhooks/stripe", StripeWebhookHandler(orders, { "journey-secret" }, { 1000L }, PaidOrderCartCleaner(orders, carts)::clear, { id -> try { confirmation.onPaid(id) } catch (error: Exception) { error.printStackTrace(); throw error } }))
            ))
            app.createContext("/", module::handle); app.start()
            val base = "http://127.0.0.1:${app.address.port}"
            call(base, "/register", "POST", """{"firstName":"Miguel","lastName":"Journey","email":"$runId@example.com","password":"test-password","phone":"+351900000000","customerType":"private","deliveryAddress":{"addressLine1":"Test street","townOrCity":"Lisbon","postcode":"1000-001","country":"PT"}}""").expect(201)
            val token = call(base, "/login", "POST", """{"email":"$runId@example.com","password":"test-password"}""").expect(200).getValue("token").jsonPrimitive.content
            println("PASS: register and login through HTTP")
            admins += requireNotNull(users.getUserByEmail("$runId@example.com")).id
            fun save(session: String) = call(base, "/cart", "POST", """{"sessionId":"$session","products":[{"productId":1,"quantityM2":1.0,"isSample":false}]}""", token).expect(201)
            fun checkout(session: String) = call(base, "/checkout", "POST", """{"sessionId":"$session","successUrl":"https://example.com/success","cancelUrl":"https://example.com/cancel"}""", token).expect(201).getValue("id").jsonPrimitive.content
            fun messages() = call(emailUrl, "/_test/messages", token = key).expect(200).getValue("messages").jsonArray
            fun webhook(id: String, type: String = "checkout.session.completed", paid: Boolean = true, signed: Boolean = true): NetworkResponse<String> {
                val raw = """{"type":"$type","data":{"object":{"id":"$id","payment_status":"${if (paid) "paid" else "unpaid"}","amount_total":11500,"currency":"eur","payment_intent":"pi_fake"}}}"""
                val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec("journey-secret".toByteArray(), "HmacSHA256"))
                val digest = mac.doFinal("1000.$raw".toByteArray()).joinToString("") { "%02x".format(it) }
                return call(base, "/webhooks/stripe", "POST", raw, signature = if (signed) "t=1000,v1=$digest" else null)
            }
            save("first"); save("second")
            call(base, "/cart?sessionId=first", token = token).expect(200)
            call(base, "/cart?sessionId=second", token = token).expect(200)
            println("PASS: two independent carts for one user")
            val id = checkout("first")
            leadTime = "8-12 Weeks" // Later catalogue edits must not rewrite the checkout estimate.
            webhook(id, signed = false).expect(400)
            webhook(id, paid = false).expect(200)
            assertTrue(messages().isEmpty())
            webhook(id).expect(200)
            assertEquals(404, call(base, "/cart?sessionId=first", token = token).statusCode())
            call(base, "/cart?sessionId=second", token = token).expect(200)
            assertEquals(1, messages().size)
            val message = messages().single().jsonObject
            assertEquals("miguelangelorios5f@gmail.com", message.getValue("to").jsonPrimitive.content)
            assertTrue(message.getValue("html").jsonPrimitive.content.contains("2-4 Weeks"))
            assertFalse(message.getValue("html").jsonPrimitive.content.contains("8-12 Weeks"))
            val attachments = message.getValue("attachments").jsonArray.map { it.jsonObject }
            assertTrue(attachments.any { it["cid"]?.jsonPrimitive?.content == "deferranti-logo" })
            val pdf = attachments.single { it["filename"]?.jsonPrimitive?.content == "payment-summary.pdf" }
            val bytes = Base64.getDecoder().decode(pdf.getValue("contentBase64").jsonPrimitive.content)
            assertEquals("%PDF-", bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII))
            assertTrue(bytes.size > 1000)
            println("PASS: paid webhook, purchased cart cleared, other cart kept, rendered email and real PDF captured")
            repeat(2) { webhook(id).expect(200) }
            assertEquals(1, messages().size)
            val history = call(emailUrl, "/emails?orderReference=$id", token = key).expect(200).getValue("emails").jsonArray
            assertEquals(1, history.size); assertEquals("accepted", history.single().jsonObject.getValue("status").jsonPrimitive.content)
            println("PASS: duplicate webhooks produce one email and one accepted history record")
            val delayed = checkout("second")
            webhook(delayed, paid = false).expect(200); assertEquals(1, messages().size)
            webhook(delayed, "checkout.session.async_payment_succeeded").expect(200)
            assertEquals(2, messages().size)
            save("expired")
            val expired = checkout("expired")
            webhook(expired, "checkout.session.expired", false).expect(200)
            assertEquals(2, messages().size)
            call(base, "/cart?sessionId=expired", token = token).expect(200)
            assertEquals(3, call(base, "/orders", token = token).expect(200).getValue("orders").jsonArray.size)
            println("PASS: delayed payment sends only after success; expired checkout retains its cart and sends nothing")
            // Simulate later fulfilment events explicitly; these are not automatic production triggers.
            fun documentData() = buildJsonObject {
                put("references", buildJsonObject {
                    put("customerOrderNumber", id); put("invoiceNumber", "TEST-INV")
                    put("purchaseOrderNumber", "TEST-PO"); put("purchaseOrderDate", "2026-10-30")
                    put("requiredDespatchDate", "2026-11-15"); put("packingListNumber", "TEST-PL")
                    put("packingDate", "2026-11-14"); put("deliveryNoteNumber", "TEST-DN"); put("deliveryDate", "2026-11-18")
                })
                put("parties", buildJsonObject {
                    listOf("purchaser", "supplier", "shipFrom", "deliverTo", "importerOfRecord", "seller").forEach {
                        put(it, buildJsonObject { put("name", "Journey test $it"); put("addressLines", buildJsonArray { add("Test street, Lisbon, PT") }) })
                    }
                })
                put("goods", buildJsonArray { add(buildJsonObject {
                    put("productName", "Journey tile"); put("area", "1 m2"); put("boxes", "2"); put("tiles", "Test quantity")
                    put("unitPrice", "EUR 90.00"); put("lineTotal", "EUR 90.00"); put("deFerrantiSku", "TEST-TILE")
                    put("hsCode", "Test only"); put("originCountry", "PT")
                }) })
                put("totals", buildJsonObject { put("supplierSubtotal", "EUR 90.00"); put("vatTreatment", "TEST DOCUMENT"); put("purchaseOrderTotal", "EUR 115.00") })
                put("packing", buildJsonObject { put("packageCount", "2"); put("grossWeight", "10 kg"); put("dimensions", "40 x 40 x 20 cm") })
                put("shipment", buildJsonObject { put("courier", "Test courier"); put("trackingNumber", "TEST-TRACKING"); put("incoterm", "DAP") })
            }
            val lifecycle = listOf(
                OrderEmailTemplate.ORDER_CONFIRMATION,
                OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER,
                OrderEmailTemplate.SUPPLIER_PACKING_LIST,
                OrderEmailTemplate.SUPPLIER_DELIVERY_NOTE,
                OrderEmailTemplate.REVISED_DESPATCH_DATE,
                OrderEmailTemplate.GOODS_DESPATCHED,
                OrderEmailTemplate.DELIVERY_CONFIRMATION,
                OrderEmailTemplate.POST_DELIVERY_THANK_YOU,
                OrderEmailTemplate.INCOMPLETE_CHECKOUT
            )
            lifecycle.forEach { template ->
                val fields = buildJsonObject {
                    when (template) {
                        OrderEmailTemplate.SUPPLIER_PACKING_LIST -> put("quantity_units", "2")
                        OrderEmailTemplate.REVISED_DESPATCH_DATE -> { put("previous_despatch_date", "2026-11-15"); put("revised_despatch_date", "2026-11-20") }
                        OrderEmailTemplate.GOODS_DESPATCHED -> { put("courier", "Test courier"); put("tracking_number", "TEST-TRACKING"); put("estimated_delivery_date", "2026-11-21"); put("tracking_url", "https://example.com/test-tracking") }
                        OrderEmailTemplate.DELIVERY_CONFIRMATION -> put("delivery_date", "2026-11-21")
                        OrderEmailTemplate.POST_DELIVERY_THANK_YOU -> { put("discount_code", "TEST-NOT-REDEEMABLE"); put("marketing_consent_link", "https://example.com/test-consent") }
                        else -> Unit
                    }
                }
                val request = buildJsonObject {
                    put("templateId", template.id)
                    put("context", buildJsonObject { put("fields", fields); if (template.supplier) put("supplierId", "journey-supplier") })
                    if (template.documentType != null) put("documentData", documentData())
                }.toString()
                val reference = if (template == OrderEmailTemplate.INCOMPLETE_CHECKOUT) expired else id
                val before = messages().size
                call(base, "/admin/orders/$reference/send-email", "POST", request, token).expect(202)
                assertEquals(before + 1, messages().size)
                // Every template, including supplier mail, is forced to the test destination.
                assertEquals("miguelangelorios5f@gmail.com", messages().last().jsonObject.getValue("to").jsonPrimitive.content)
                call(base, "/admin/orders/$reference/send-email", "POST", request, token).expect(202)
                assertEquals(before + 1, messages().size)
            }
            val capturedPdfs = messages().flatMap { it.jsonObject.getValue("attachments").jsonArray }.map { it.jsonObject }
                .filter { it["contentType"]?.jsonPrimitive?.content == "application/pdf" }
            assertTrue(capturedPdfs.map { it.getValue("filename").jsonPrimitive.content }.containsAll(listOf("invoice.pdf", "purchase-order.pdf", "packing-list.pdf", "delivery-note.pdf")))
            capturedPdfs.forEach {
                val content = Base64.getDecoder().decode(it.getValue("contentBase64").jsonPrimitive.content)
                assertEquals("%PDF-", content.copyOfRange(0, 5).toString(Charsets.US_ASCII))
            }
            println("PASS: all nine additional lifecycle templates, four document generators, customer/supplier recipient override and replay protection")
        } finally {
            try { emailBase?.let { call(it, "/_test/cleanup", "POST", "{}", key).expect(200) } }
            finally { app.stop(0); stripe.stop(0); emailProcess.destroy(); if (!emailProcess.waitFor(5, TimeUnit.SECONDS)) emailProcess.destroyForcibly() }
        }
    }
}
