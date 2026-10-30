package http.admin

import db.OrderRepository
import db.offline.InMemoryOrderRepository
import db.offline.InMemoryUserRepository
import db.offline.InMemoryAuthTokenRepository
import http.HttpRequest
import services.auth.AuthManager
import services.user.UserManager
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class GetOrdersHandlerTest {
 private val users=InMemoryUserRepository()
 private val auth=AuthManager(users,InMemoryAuthTokenRepository())
 private val admin=UserManager(users).registerUser("admin@example.com","password-123")
 private val customer=UserManager(users).registerUser("customer@example.com","password-123")
 private val orders=InMemoryOrderRepository()
 private fun request(email:String?=null)=HttpRequest("GET","/admin/orders","",headers=email?.let{mapOf("Authorization" to listOf("Bearer ${auth.login(it,"password-123").token}"))}.orEmpty())
 @Test fun requiresAdministratorLogin() {
  val handler=GetOrdersHandler(auth,orders,setOf(admin.id))
  assertEquals(401,handler.handle(request()).statusCode)
  assertEquals(403,handler.handle(request(customer.email)).statusCode)
  assertEquals(403,GetOrdersHandler(auth,orders,emptySet()).handle(request(admin.email)).statusCode)
 }
 @Test fun administratorSeesOrdersAcrossCustomersAndGuestOrders() {
  for((id,userId) in listOf("first" to customer.id,"second" to admin.id,"guest" to null)) {
   orders.create(id,userId,buildJsonObject{put("total","25.00");put("currency","eur")})
  }
  val response=GetOrdersHandler(auth,orders,setOf(admin.id)).handle(request(admin.email))
  assertEquals(200,response.statusCode)
  val rows=Json.parseToJsonElement(response.body).jsonObject.getValue("orders").jsonArray
  assertEquals(3,rows.size)
  assertEquals(setOf("first","second","guest"),rows.map{it.jsonObject.getValue("checkoutSessionId").jsonPrimitive.content}.toSet())
  assertFalse(rows.any{it.jsonObject.containsKey("snapshot")})
  assertEquals(1,orders.list(customer.id).size)
 }
 @Test fun emptyOrdersAndSafeFailures() {
  val response=GetOrdersHandler(auth,orders,setOf(admin.id)).handle(request(admin.email))
  assertEquals("[]",Json.parseToJsonElement(response.body).jsonObject["orders"].toString())
  val broken=object:OrderRepository by orders {override fun listAll():List<JsonObject> = error("secret database details")}
  val failure=GetOrdersHandler(auth,broken,setOf(admin.id)).handle(request(admin.email))
  assertEquals(503,failure.statusCode)
  assertFalse(failure.body.contains("secret"))
 }
}
