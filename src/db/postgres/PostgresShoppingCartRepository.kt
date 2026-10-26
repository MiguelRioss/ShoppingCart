@file:Suppress("SqlNoDataSourceInspection")

/**
 * PostgreSQL-backed storage for carts and their line items.
 */
package db.postgres

import ShoppingCartProduct
import db.ShoppingCartRepository
import domain.cart.ShoppingCart
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class PostgresShoppingCartRepository(
    private val database: Database
) : ShoppingCartRepository {
    override fun getCartByUserId(userId: UUID): ShoppingCart? {
        database.getConnection().use { connection ->
            val cart = connection.prepareStatement(
                "SELECT id, user_id, session_id, date_time FROM shopping_carts WHERE user_id = ?"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toCartWithoutProducts() else null
                }
            } ?: return null

            return cart.copy(products = getProductsByCartId(cart.id))
        }
    }

    override fun getCartBySessionId(sessionId: String): ShoppingCart? {
        database.getConnection().use { connection ->
            val cart = connection.prepareStatement(
                "SELECT id, user_id, session_id, date_time FROM shopping_carts WHERE session_id = ?"
            ).use { statement ->
                statement.setString(1, sessionId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toCartWithoutProducts() else null
                }
            } ?: return null

            return cart.copy(products = getProductsByCartId(cart.id))
        }
    }

    private fun getProductsByCartId(cartId: UUID): List<ShoppingCartProduct> {
        database.getConnection().use { connection ->
            return connection.prepareStatement(
                """
                SELECT product_id, square_meters, amount_boxes, total_price_per_product, is_sample, sample_units
                FROM shopping_cart_products
                WHERE cart_id = ?
                ORDER BY product_id
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, cartId)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toCartProduct())
                        }
                    }
                }
            }
        }
    }

    override fun clearCartBySessionId(sessionId: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("DELETE FROM shopping_carts WHERE session_id = ?").use { statement ->
                statement.setString(1, sessionId)
                return statement.executeUpdate() > 0
            }
        }
    }

    override fun saveCart(cart: ShoppingCart): ShoppingCart {
        var savedCart = cart
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                // Serialize saves for a user, including concurrent saves from new browser sessions.
                cart.userId?.let { userId ->
                    connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use {
                        it.setString(1, userId.toString()); it.execute()
                    }
                    connection.prepareStatement("SELECT id FROM shopping_carts WHERE user_id = ?").use {
                        it.setObject(1, userId)
                        it.executeQuery().use { rows -> if (rows.next()) savedCart = cart.copy(id = rows.getObject("id", UUID::class.java)) }
                    }
                }
                connection.prepareStatement(
                    """
                    INSERT INTO shopping_carts (id, user_id, session_id, date_time)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        user_id = EXCLUDED.user_id,
                        session_id = EXCLUDED.session_id,
                        date_time = EXCLUDED.date_time
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, savedCart.id)
                    statement.setObject(2, cart.userId)
                    statement.setString(3, cart.sessionId)
                    statement.setTimestamp(4, Timestamp.valueOf(cart.dateTime))
                    statement.executeUpdate()
                }

                if (cart.userId != null && cart.sessionId != null) {
                    connection.prepareStatement("DELETE FROM shopping_carts WHERE session_id = ? AND id <> ? AND user_id IS NULL").use {
                        it.setString(1, cart.sessionId); it.setObject(2, savedCart.id); it.executeUpdate()
                    }
                }

                connection.prepareStatement("DELETE FROM shopping_cart_products WHERE cart_id = ?").use { statement ->
                    statement.setObject(1, savedCart.id)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO shopping_cart_products (
                        cart_id,
                        product_id,
                        square_meters,
                        amount_boxes,
                        total_price_per_product,
                        is_sample,
                        sample_units
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    cart.products.forEach { product ->
                        statement.setObject(1, savedCart.id)
                        statement.setLong(2, product.productId)
                        statement.setObject(3, product.squareMeters)
                        statement.setObject(4, product.amountBoxes)
                        statement.setBigDecimal(5, product.totalPricePerProduct)
                        statement.setBoolean(6, product.isSample)
                        statement.setObject(7, product.sampleUnits)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }

                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }

        return savedCart
    }

    private fun ResultSet.toCartWithoutProducts(): ShoppingCart =
        ShoppingCart(
            id = getObject("id", UUID::class.java),
            userId = getObject("user_id", UUID::class.java),
            dateTime = getTimestamp("date_time").toLocalDateTime(),
            sessionId = getString("session_id")
        )

    private fun ResultSet.toCartProduct(): ShoppingCartProduct =
        ShoppingCartProduct(
            productId = getLong("product_id"),

            squareMeters =
                getObject(
                    "square_meters",
                    Double::class.javaObjectType
                ),

            amountBoxes =
                getObject(
                    "amount_boxes",
                    Int::class.javaObjectType
                ),

            totalPricePerProduct =
                getBigDecimal("total_price_per_product"),

            isSample =
                getBoolean("is_sample"),

            sampleUnits =
                getObject(
                    "sample_units",
                    Int::class.javaObjectType
                )
        )
}
