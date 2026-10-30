package http.admin

import db.offline.*
import domain.user.User
import http.HttpRequest
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import services.auth.AuthManager
import services.orderemail.*
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

class SendOrderEmailHandlerTest {
 private val users=InMemoryUserRepository()
 private val admin=users.saveUser(User(UUID.randomUUID(),"admin@example.com","unused",LocalDateTime.now(),firstName="Admin"))
 private val other=users.saveUser(admin.copy(id=UUID.randomUUID(),email="other@example.com"))
 private val orders=InMemoryOrderRepository()
 private val auth=AuthManager(users,InMemoryAuthTokenRepository())
 private val catalogue=object:ProductDataAccess {
  override fun getAllProducts()="[]"
  override fun getPurchasableProducts()="[]"
  override fun getProductBySlug(productSlug:String)="{}"
  override fun getProductById(productId:Long)="{}"
 }
 private var calls=0
 private val sender=EmailSenderClient{calls++;buildJsonObject{put("messageId","mock")}}
 private val handler=SendOrderEmailHandler(auth,SendOrderEmailService(orders,OrderEmailPayloadBuilder(users,catalogue),sender),setOf(admin.id))
 private fun request(body:String,user:User?=admin):HttpRequest {
  val headers=user?.let {
   val token="test-${it.id}";auth.authTokenRepository.saveToken(domain.auth.AuthToken(token,it.id,LocalDateTime.now().plusHours(1)))
   mapOf("Authorization" to listOf("Bearer $token"))
  }.orEmpty()
  return HttpRequest("POST","/admin/orders/order/send-email",body,headers,pathParameters=mapOf("orderId" to "order"))
 }
 @Test fun protectsExplicitSendAction() {
  assertEquals(401,handler.handle(request("{}",null)).statusCode)
  assertEquals(403,handler.handle(request("{}",other)).statusCode)
  assertEquals(0,calls)
 }
 @Test fun supportsNonPdfEmailButRequiresDocumentDataForInvoice() {
  orders.create("order",admin.id,buildJsonObject{put("customerEmail",admin.email);put("customerFirstName","Admin")})
  orders.updatePayment("order","PAID",100,"eur",null)
  val response=handler.handle(request("""{"templateId":"delivery-confirmation","context":{"fields":{"delivery_date":"2026-11-19"}}}"""))
  assertEquals(202,response.statusCode);assertEquals(1,calls)
  assertEquals(422,handler.handle(request("""{"templateId":"order-confirmation"}""")).statusCode)
  assertEquals(1,calls)
 }
 @Test fun rejectsMalformedBodyWithoutAnySend() {
  assertEquals(400,handler.handle(request("not-json")).statusCode)
  assertEquals(400,handler.handle(request("""{"templateId":"unknown"}""")).statusCode)
  assertEquals(0,calls)
 }
}
