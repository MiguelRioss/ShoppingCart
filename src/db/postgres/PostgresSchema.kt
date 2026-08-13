package db.postgres

/**
 * Creates the database tables required by the application.
 */
class PostgresSchema(
    private val database: Database
) {
    fun migrate() {
        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id UUID PRIMARY KEY,
                        email TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        first_name TEXT,
                        last_name TEXT,
                        phone TEXT,
                        customer_type TEXT,
                        delivery_company TEXT,
                        delivery_address_line1 TEXT,
                        delivery_address_line2 TEXT,
                        delivery_town_or_city TEXT,
                        delivery_postcode TEXT,
                        delivery_country TEXT,
                        same_as_delivery_address BOOLEAN NOT NULL DEFAULT TRUE,
                        invoice_company TEXT,
                        invoice_address_line1 TEXT,
                        invoice_address_line2 TEXT,
                        invoice_town_or_city TEXT,
                        invoice_postcode TEXT,
                        invoice_country TEXT,
                        vat_number TEXT,
                        project_notes TEXT
                    )
                    """.trimIndent()
                )
                addColumnIfMissing(statement, "users", "first_name", "TEXT")
                addColumnIfMissing(statement, "users", "last_name", "TEXT")
                addColumnIfMissing(statement, "users", "phone", "TEXT")
                addColumnIfMissing(statement, "users", "customer_type", "TEXT")
                addColumnIfMissing(statement, "users", "delivery_company", "TEXT")
                addColumnIfMissing(statement, "users", "delivery_address_line1", "TEXT")
                addColumnIfMissing(statement, "users", "delivery_address_line2", "TEXT")
                addColumnIfMissing(statement, "users", "delivery_town_or_city", "TEXT")
                addColumnIfMissing(statement, "users", "delivery_postcode", "TEXT")
                addColumnIfMissing(statement, "users", "delivery_country", "TEXT")
                addColumnIfMissing(statement, "users", "same_as_delivery_address", "BOOLEAN NOT NULL DEFAULT TRUE")
                addColumnIfMissing(statement, "users", "invoice_company", "TEXT")
                addColumnIfMissing(statement, "users", "invoice_address_line1", "TEXT")
                addColumnIfMissing(statement, "users", "invoice_address_line2", "TEXT")
                addColumnIfMissing(statement, "users", "invoice_town_or_city", "TEXT")
                addColumnIfMissing(statement, "users", "invoice_postcode", "TEXT")
                addColumnIfMissing(statement, "users", "invoice_country", "TEXT")
                addColumnIfMissing(statement, "users", "vat_number", "TEXT")
                addColumnIfMissing(statement, "users", "project_notes", "TEXT")
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS auth_tokens (
                        token TEXT PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        expires_at TIMESTAMP NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_carts (
                        id UUID PRIMARY KEY,
                        user_id UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                        session_id TEXT,
                        date_time TIMESTAMP NOT NULL
                    )
                    """.trimIndent()
                )
                addColumnIfMissing(statement, "shopping_carts", "session_id", "TEXT")
                statement.executeUpdate("ALTER TABLE shopping_carts ALTER COLUMN user_id DROP NOT NULL")
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_cart_products (
                        cart_id UUID NOT NULL REFERENCES shopping_carts(id) ON DELETE CASCADE,
                        product_id BIGINT NOT NULL,
                        square_meters DOUBLE PRECISION NOT NULL,
                        amount_boxes INTEGER NOT NULL,
                        total_price_per_product NUMERIC(12, 2) NOT NULL,
                        PRIMARY KEY (cart_id, product_id)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun addColumnIfMissing(statement: java.sql.Statement, table: String, column: String, type: String) {
        statement.executeUpdate("ALTER TABLE $table ADD COLUMN IF NOT EXISTS $column $type")
    }
}
