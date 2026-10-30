package http.admin

import db.offline.*
import domain.user.User
import http.HttpRequest
import kotlinx.serialization.json.*
import productdatabaseaccesslayer.ProductDataAccess
import services.auth.AuthManager
import services.orderemail.OrderEmailPayloadBuilder
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

class PrepareOrderEmailHandlerTest {
 private val users=InMemoryUserRepository()
 private val admin=users.saveUser(User(UUID.randomUUID(),"admin@example.com","unused",LocalDateTime.now(),firstName="Admin"))
 private val customer=users.saveUser(admin.copy(id=UUID.randomUUID(),email="customer@example.com",firstName="Customer"))
 private val auth=AuthManager(users,InMemoryAuthTokenRepository())
 private val orders=InMemoryOrderRepository()
 private val catalogue=object:ProductDataAccess {
  override fun getAllProducts()="[]"
  override fun getPurchasableProducts()="[]"
  override fun getProductBySlug(productSlug:String)="{}"
  override fun getProductById(productId:Long)="{}"
 }
 private val handler=PrepareOrderEmailHandler(auth,orders,OrderEmailPayloadBuilder(users,catalogue),setOf(admin.id))
 private fun token(user:User):String {
  val token="token-${user.id}"
  auth.authTokenRepository.saveToken(domain.auth.AuthToken(token,user.id,LocalDateTime.now().plusHours(1)))
  return token
 }
 private fun request(body:String="""{"templateId":"incomplete-checkout"}""",user:User?=admin,id:String="order")=HttpRequest("POST","/admin/orders/$id/email-payload",body,headers=user?.let{mapOf("Authorization" to listOf("Bearer ${token(it)}"))}.orEmpty(),pathParameters=mapOf("orderId" to id))
 private fun saveOrder() {
  orders.create("order",customer.id,Json.parseToJsonElement("""{"customerEmail":"customer@example.com","customerFirstName":"Customer","items":[{"productId":1,"name":"Backend title","quantity":1,"amountTotal":"90.00","currency":"eur","imageUrl":"https://example.com/tile.jpg"}],"cartProducts":[{"productId":1,"isSample":false,"squareMeters":1.5,"amountBoxes":1}]}""").jsonObject)
 }
 @Test fun protectsPreparationWithAdministratorAuthentication() {
  assertEquals(401,handler.handle(request(user=null)).statusCode)
  assertEquals(403,handler.handle(request(user=customer)).statusCode)
 }
 @Test fun preparesJsonWithoutSendingOrUpdatingTheOrder() {
  saveOrder()
  val response=handler.handle(request())
  assertEquals(200,response.statusCode)
  val payload=Json.parseToJsonElement(response.body).jsonObject
  assertEquals("incomplete-checkout",payload["templateId"]!!.jsonPrimitive.content)
  assertEquals("PENDING_PAYMENT",orders.findForEmail("order")!!["status"]!!.jsonPrimitive.content)
 }
 @Test fun unknownOrderAndBadInputsAreExplicit() {
  assertEquals(404,handler.handle(request()).statusCode)
  assertEquals(400,handler.handle(request("not-json")).statusCode)
  assertEquals(400,handler.handle(request("""{"templateId":"unknown"}""")).statusCode)
  assertEquals(400,handler.handle(request("""{"templateId":"incomplete-checkout","context":[]} """)).statusCode)
 }
 @Test fun workflowDataAndPaymentStateAreValidated() {
  saveOrder()
  assertEquals(409,handler.handle(request("""{"templateId":"order-confirmation"}""")).statusCode)
  orders.updatePayment("order","PAID",9000,"eur",null)
  val response=handler.handle(request("""{"templateId":"order-confirmation"}"""))
  assertEquals(422,response.statusCode)
  assertTrue(response.body.contains("invoice_number"))
 }
}
