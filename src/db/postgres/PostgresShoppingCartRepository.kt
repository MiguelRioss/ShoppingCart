package db.postgres

import ShoppingCartProduct
import db.ShoppingCartRepository
import domain.cart.ShoppingCart
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class PostgresShoppingCartRepository(private val database: Database) : ShoppingCartRepository {
    override fun getCartById(cartId: UUID): ShoppingCart? = findCart("SELECT * FROM shopping_carts WHERE id = ?", cartId)
    override fun clearCartIfUnchanged(cartId: UUID, savedAt: java.time.LocalDateTime): Boolean = database.getConnection().use { connection ->
        connection.prepareStatement("DELETE FROM shopping_carts WHERE id = ? AND date_time = ?").use {
            it.setObject(1, cartId); it.setTimestamp(2, Timestamp.valueOf(savedAt)); it.executeUpdate() > 0
        }
    }
    override fun getCartByUserId(userId: UUID): ShoppingCart? = findCart(
        "SELECT * FROM shopping_carts WHERE user_id = ? ORDER BY date_time DESC, id DESC LIMIT 1", userId)
    override fun getCartBySessionId(sessionId: String): ShoppingCart? = findCart(
        "SELECT * FROM shopping_carts WHERE session_id = ?", sessionId)

    private fun findCart(sql: String, value: Any): ShoppingCart? = database.getConnection().use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, value)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.toCart().let { it.copy(products = products(connection, it.id)) }
            }
        }
    }
    private fun products(connection: Connection, cartId: UUID): List<ShoppingCartProduct> =
        connection.prepareStatement("SELECT * FROM shopping_cart_products WHERE cart_id = ? ORDER BY product_id, is_sample").use { statement ->
            statement.setObject(1, cartId)
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add(ShoppingCartProduct(
                    productId = rows.getLong("product_id"),
                    squareMeters = rows.getObject("square_meters", Double::class.javaObjectType),
                    amountBoxes = rows.getObject("amount_boxes", Int::class.javaObjectType),
                    totalPricePerProduct = rows.getBigDecimal("total_price_per_product"),
                    isSample = rows.getBoolean("is_sample"),
                    sampleUnits = rows.getObject("sample_units", Int::class.javaObjectType)
                ))
            } }
        }
    override fun clearCartBySessionId(sessionId: String): Boolean = database.getConnection().use { connection ->
        connection.prepareStatement("DELETE FROM shopping_carts WHERE session_id = ?").use {
            it.setString(1, sessionId); it.executeUpdate() > 0
        }
    }
    override fun saveCart(cart: ShoppingCart): ShoppingCart = database.getConnection().use { connection ->
        connection.autoCommit = false
        try {
            var saved = cart
            cart.sessionId?.let { sessionId ->
                connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use {
                    it.setString(1, sessionId); it.execute()
                }
                connection.prepareStatement("SELECT id, user_id FROM shopping_carts WHERE session_id = ?").use {
                    it.setString(1, sessionId)
                    it.executeQuery().use { rows -> if (rows.next()) {
                        val owner = rows.getObject("user_id", UUID::class.java)
                        require(owner == null || owner == cart.userId) { "This shopping cart belongs to another user" }
                        saved = cart.copy(id = rows.getObject("id", UUID::class.java))
                    } }
                }
            }
            connection.prepareStatement("""INSERT INTO shopping_carts (id, user_id, session_id, date_time) VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET user_id = EXCLUDED.user_id, session_id = EXCLUDED.session_id, date_time = EXCLUDED.date_time""").use {
                it.setObject(1, saved.id); it.setObject(2, saved.userId); it.setString(3, saved.sessionId)
                it.setTimestamp(4, Timestamp.valueOf(saved.dateTime)); it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM shopping_cart_products WHERE cart_id = ?").use {
                it.setObject(1, saved.id); it.executeUpdate()
            }
            connection.prepareStatement("""INSERT INTO shopping_cart_products
                (cart_id, product_id, square_meters, amount_boxes, total_price_per_product, is_sample, sample_units)
                VALUES (?, ?, ?, ?, ?, ?, ?)""").use { statement ->
                saved.products.forEach { product ->
                    statement.setObject(1, saved.id); statement.setLong(2, product.productId)
                    statement.setObject(3, product.squareMeters); statement.setObject(4, product.amountBoxes)
                    statement.setBigDecimal(5, product.totalPricePerProduct); statement.setBoolean(6, product.isSample)
                    statement.setObject(7, product.sampleUnits); statement.addBatch()
                }
                statement.executeBatch()
            }
            connection.commit()
            saved
        } catch (error: Exception) { connection.rollback(); throw error }
        finally { connection.autoCommit = true }
    }
    private fun ResultSet.toCart() = ShoppingCart(
        id = getObject("id", UUID::class.java), userId = getObject("user_id", UUID::class.java),
        dateTime = getTimestamp("date_time").toLocalDateTime(), sessionId = getString("session_id")
    )
}
