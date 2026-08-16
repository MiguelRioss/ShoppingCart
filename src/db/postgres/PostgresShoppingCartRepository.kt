package db.postgres

import db.ShoppingCartRepository
import domain.ShoppingCart
import domain.ShoppingCartProduct
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
                SELECT product_id, square_meters, amount_boxes, total_price_per_product
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
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
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
                    statement.setObject(1, cart.id)
                    statement.setObject(2, cart.userId)
                    statement.setString(3, cart.sessionId)
                    statement.setTimestamp(4, Timestamp.valueOf(cart.dateTime))
                    statement.executeUpdate()
                }

                connection.prepareStatement("DELETE FROM shopping_cart_products WHERE cart_id = ?").use { statement ->
                    statement.setObject(1, cart.id)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO shopping_cart_products (
                        cart_id,
                        product_id,
                        square_meters,
                        amount_boxes,
                        total_price_per_product
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    cart.products.forEach { product ->
                        statement.setObject(1, cart.id)
                        statement.setLong(2, product.productId)
                        statement.setDouble(3, product.squareMeters)
                        statement.setInt(4, product.amountBoxes)
                        statement.setBigDecimal(5, product.totalPricePerProduct)
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

        return cart
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
            squareMeters = getDouble("square_meters"),
            amountBoxes = getInt("amount_boxes"),
            totalPricePerProduct = getBigDecimal("total_price_per_product")
        )
}
