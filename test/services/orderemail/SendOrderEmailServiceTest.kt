package services.orderemail

import com.sun.net.httpserver.HttpServer
import db.offline.InMemoryOrderRepository
import db.offline.InMemoryUserRepository
import domain.user.User
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import services.pdf.TestDeFerrantiOrderDocuments
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import kotlin.test.*

class SendOrderEmailServiceTest {
    private val users=InMemoryUserRepository()
    private val user=users.saveUser(User(UUID.randomUUID(),"customer@example.com","never-send",LocalDateTime.now(),firstName="Customer"))
    private val orders=InMemoryOrderRepository()
    private val data=TestDeFerrantiOrderDocuments.create()
    private val orderId=data.references.customerOrderNumber
    private val catalogue=object:ProductDataAccess {
        override fun getAllProducts()="[]"
        override fun getPurchasableProducts()="[]"
        override fun getProductBySlug(productSlug:String)="{}"
        override fun getProductById(productId:Long)="""{"image":"https://example.com/image.png","supplier":{"id":"supplier-1","contact_name":"Supplier","email":"supplier@example.com","supplier_product_id":"REAL-REFERENCE"}}"""
    }
    private val builder=OrderEmailPayloadBuilder(users,catalogue)
    private fun saveOrder() {
        orders.create(orderId,user.id,buildJsonObject {
            put("customerEmail",user.email);put("customerFirstName","Customer")
            put("items",buildJsonArray{data.goods.forEachIndexed{index,goods->add(buildJsonObject{
                put("productId",index+1);put("name",goods.productName);put("quantity",1);put("amountTotal","90.00");put("currency","eur");put("imageUrl","https://example.com/image.png")
            })}})
            put("cartProducts",buildJsonArray{data.goods.forEachIndexed{index,_->add(buildJsonObject{
                put("productId",index+1);put("isSample",false);put("squareMeters",1.5);put("amountBoxes",1)
            })}})
        })
        orders.updatePayment(orderId,"PAID",9000,"eur",null)
    }
    private fun context(template:OrderEmailTemplate)=buildJsonObject {
        if(template.supplier)put("supplierId","supplier-1")
        put("fields",buildJsonObject {
            when(template) {
                OrderEmailTemplate.ORDER_CONFIRMATION -> put("expected_despatch_date","2026-09-24")
                OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER -> put("required_despatch_date","2026-09-24")
                OrderEmailTemplate.SUPPLIER_PACKING_LIST -> {
                    put("quantity_units","25");put("package_count",data.packing.packageCount);put("gross_weight",data.packing.grossWeight);put("package_dimensions",data.packing.dimensions)
                }
                OrderEmailTemplate.SUPPLIER_DELIVERY_NOTE -> {
                    put("courier",data.shipment.courier);put("tracking_number",data.shipment.trackingNumber);put("incoterm",data.shipment.incoterm)
                }
                else -> Unit
            }
        })
    }
    @Test fun generatesAllFourRealPdfTypesAndPostsBytesToV2Send() {
        saveOrder()
        val received=mutableListOf<JsonObject>()
        val paths=mutableListOf<String>()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/"){exchange->
            paths+=exchange.requestURI.path
            val payload=Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
            assertEquals(payload.getValue("idempotencyKey").jsonPrimitive.content,exchange.requestHeaders.getFirst("Idempotency-Key"))
            assertEquals("Bearer test-key",exchange.requestHeaders.getFirst("Authorization"))
            received+=payload
            val response="""{"messageId":"mock-accepted"}""".toByteArray()
            exchange.sendResponseHeaders(202,response.size.toLong());exchange.responseBody.use{it.write(response)}
        }
        server.start()
        try {
            val client=HttpEmailSenderClient({"http://127.0.0.1:${server.address.port}"},{"test-key"})
            val service=SendOrderEmailService(orders,builder,client)
            val templates=listOf(OrderEmailTemplate.ORDER_CONFIRMATION,OrderEmailTemplate.SUPPLIER_PURCHASE_ORDER,OrderEmailTemplate.SUPPLIER_PACKING_LIST,OrderEmailTemplate.SUPPLIER_DELIVERY_NOTE)
            templates.forEach{template->
                val result=service.send(orderId,template,context(template),data)
                assertEquals("submitted",result.getValue("status").jsonPrimitive.content)
                val payload=received.last()
                assertEquals(template.id,payload.getValue("templateId").jsonPrimitive.content)
                val attachment=payload.getValue("attachments").jsonArray.single().jsonObject
                val bytes=Base64.getDecoder().decode(attachment.getValue("contentBase64").jsonPrimitive.content)
                assertEquals("%PDF-",bytes.copyOfRange(0,5).toString(Charsets.US_ASCII));assertTrue(bytes.size>1000)
                assertEquals(template.documentType,attachment.getValue("type").jsonPrimitive.content)
                assertFalse(attachment.containsKey("url"));assertFalse(attachment.containsKey("path"))
                assertEquals(if(template.supplier)"supplier@example.com" else user.email,payload.getValue("to").jsonObject.getValue("email").jsonPrimitive.content)
            }
            assertEquals(List(4){"/v2/send"},paths)
        } finally {server.stop(0)}
    }
    @Test fun generationFailureAndInvalidBytesDoNotCallApi() {
        saveOrder();var calls=0
        val sender=EmailSenderClient{calls++;buildJsonObject{put("messageId","mock")}}
        val template=OrderEmailTemplate.ORDER_CONFIRMATION
        assertFailsWith<IllegalStateException>{SendOrderEmailService(orders,builder,sender){_,_->error("PDF failed")}.send(orderId,template,context(template),data)}
        assertFailsWith<IllegalArgumentException>{SendOrderEmailService(orders,builder,sender){_,_->"not PDF".toByteArray()}.send(orderId,template,context(template),data)}
        assertEquals(0,calls)
    }
    @Test fun missingOrWrongOrderDocumentIsRejected() {
        saveOrder();var calls=0
        val service=SendOrderEmailService(orders,builder,EmailSenderClient{calls++;JsonObject(emptyMap())})
        val template=OrderEmailTemplate.ORDER_CONFIRMATION
        assertFailsWith<EmailPayloadValidationException>{service.send(orderId,template,context(template),null)}
        assertFailsWith<EmailPayloadValidationException>{service.send(orderId,template,context(template),data.copy(references=data.references.copy(customerOrderNumber="wrong-order")))}
        assertEquals(0,calls)
    }
    @Test fun sender501IsNotSuccessAndDoesNotRetry() {
        var calls=0
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v2/send"){exchange->calls++;exchange.requestBody.close();exchange.sendResponseHeaders(501,-1);exchange.close()}
        server.start()
        try{
            val error=assertFailsWith<EmailSenderUnavailableException>{HttpEmailSenderClient({"http://127.0.0.1:${server.address.port}"},{null}).send(buildJsonObject{put("idempotencyKey","test")})}
            assertEquals(501,error.upstreamStatus);assertEquals(1,calls)
        }finally{server.stop(0)}
    }
    @Test fun missingSenderConfigurationFailsExplicitly() {
        assertFailsWith<EmailSenderConfigurationException>{HttpEmailSenderClient({null},{null}).send(JsonObject(emptyMap()))}
    }
}
