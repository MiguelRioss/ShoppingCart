package db.postgres

import ShoppingCartProduct
import domain.cart.ShoppingCart
import domain.user.User
import http.HttpRequest
import http.checkout.StripeWebhookHandler
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import services.checkout.core.PaidOrderCartCleaner
import services.checkout.core.cartProductsSnapshot
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/** Isolated temporary rows; no real payments, products, customers or orders are altered. */
class MultiCartPaymentIntegrationTest {
 @Test fun savesTwoCartsForOneUserAndPaidWebhookDeletesOnlyPurchasedCart() {
  val database = Database.fromEnvironment()
  assumeTrue(database != null)
  val db = requireNotNull(database)
  PostgresSchema(db).migrate()
  val users = PostgresUserRepository(db)
  val carts = PostgresShoppingCartRepository(db)
  val orders = PostgresOrderRepository(db)
  val userId = UUID.randomUUID()
  val checkoutId = "test-multicart-$userId"
  val first = ShoppingCart(UUID.randomUUID(),userId,LocalDateTime.parse("2026-10-30T10:00:00"),"test-first-$userId",listOf(ShoppingCartProduct(9278,0.5,1,BigDecimal("90.00"),false)))
  val second = first.copy(id=UUID.randomUUID(),sessionId="test-second-$userId",dateTime=first.dateTime.plusMinutes(1))
  try {
   users.saveUser(User(userId,"multicart-$userId@example.com","not-a-login",first.dateTime))
   carts.saveCart(first); carts.saveCart(second)
   assertEquals(first,carts.getCartBySessionId(first.sessionId!!))
   assertEquals(second,carts.getCartBySessionId(second.sessionId!!))
   val updated = second.copy(dateTime=second.dateTime.plusMinutes(1))
   assertEquals(second.id,carts.saveCart(updated).id)
   assertEquals(first,carts.getCartBySessionId(first.sessionId))
   orders.create(checkoutId,userId,buildJsonObject{
    put("cartId",first.id.toString()); put("cartUpdatedAt",first.dateTime.toString()); put("cartProducts",cartProductsSnapshot(first))
   })
   val raw="""{"type":"checkout.session.completed","data":{"object":{"id":"$checkoutId","payment_status":"paid","amount_total":9000,"currency":"eur"}}}"""
   val mac=Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec("integration-secret".toByteArray(),"HmacSHA256"))
   val signature=mac.doFinal("1000.$raw".toByteArray()).joinToString(""){"%02x".format(it)}
   val handler=StripeWebhookHandler(orders,{"integration-secret"},{1000},PaidOrderCartCleaner(orders,carts)::clear)
   val request=HttpRequest("POST","/webhooks/stripe",raw,mapOf("Stripe-Signature" to listOf("t=1000,v1=$signature")))
   repeat(2){assertEquals(200,handler.handle(request).statusCode)}
   assertNull(carts.getCartById(first.id))
   assertEquals(updated,carts.getCartById(second.id))
   assertEquals("PAID",orders.list(userId).single().getValue("status").jsonPrimitive.content)
   assertEquals(1,orders.list(userId).size)
  } finally {
   db.getConnection().use {connection->connection.prepareStatement("DELETE FROM orders WHERE checkout_session_id = ?").use {it.setString(1,checkoutId);it.executeUpdate()}}
   users.deleteUser(userId)
  }
 }
}
