package db.postgres

import db.UserRepository
import domain.User
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class PostgresUserRepository(
    private val database: Database
) : UserRepository {
    override fun saveUser(user: User): User {
        database.getConnection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO users (
                    id, email, password_hash, created_at, first_name, last_name, phone, customer_type,
                    delivery_company, delivery_address_line1, delivery_address_line2, delivery_town_or_city,
                    delivery_postcode, delivery_country, same_as_delivery_address, invoice_company,
                    invoice_address_line1, invoice_address_line2, invoice_town_or_city, invoice_postcode,
                    invoice_country, vat_number, project_notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    password_hash = EXCLUDED.password_hash,
                    created_at = EXCLUDED.created_at,
                    first_name = EXCLUDED.first_name,
                    last_name = EXCLUDED.last_name,
                    phone = EXCLUDED.phone,
                    customer_type = EXCLUDED.customer_type,
                    delivery_company = EXCLUDED.delivery_company,
                    delivery_address_line1 = EXCLUDED.delivery_address_line1,
                    delivery_address_line2 = EXCLUDED.delivery_address_line2,
                    delivery_town_or_city = EXCLUDED.delivery_town_or_city,
                    delivery_postcode = EXCLUDED.delivery_postcode,
                    delivery_country = EXCLUDED.delivery_country,
                    same_as_delivery_address = EXCLUDED.same_as_delivery_address,
                    invoice_company = EXCLUDED.invoice_company,
                    invoice_address_line1 = EXCLUDED.invoice_address_line1,
                    invoice_address_line2 = EXCLUDED.invoice_address_line2,
                    invoice_town_or_city = EXCLUDED.invoice_town_or_city,
                    invoice_postcode = EXCLUDED.invoice_postcode,
                    invoice_country = EXCLUDED.invoice_country,
                    vat_number = EXCLUDED.vat_number,
                    project_notes = EXCLUDED.project_notes
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, user.id)
                statement.setString(2, user.email)
                statement.setString(3, user.passwordHash)
                statement.setTimestamp(4, Timestamp.valueOf(user.createdAt))
                statement.setString(5, user.firstName)
                statement.setString(6, user.lastName)
                statement.setString(7, user.phone)
                statement.setString(8, user.customerType)
                statement.setString(9, user.deliveryCompany)
                statement.setString(10, user.deliveryAddressLine1)
                statement.setString(11, user.deliveryAddressLine2)
                statement.setString(12, user.deliveryTownOrCity)
                statement.setString(13, user.deliveryPostcode)
                statement.setString(14, user.deliveryCountry)
                statement.setBoolean(15, user.sameAsDeliveryAddress)
                statement.setString(16, user.invoiceCompany)
                statement.setString(17, user.invoiceAddressLine1)
                statement.setString(18, user.invoiceAddressLine2)
                statement.setString(19, user.invoiceTownOrCity)
                statement.setString(20, user.invoicePostcode)
                statement.setString(21, user.invoiceCountry)
                statement.setString(22, user.vatNumber)
                statement.setString(23, user.projectNotes)
                statement.executeUpdate()
            }
        }

        return user
    }

    override fun getUser(userId: UUID): User? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM users WHERE id = ?").use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.toUser() else null
                }
            }
        }
    }

    override fun getUserByEmail(email: String): User? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM users WHERE email = ?").use { statement ->
                statement.setString(1, email)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.toUser() else null
                }
            }
        }
    }

    private fun ResultSet.toUser(): User =
        User(
            id = getObject("id", UUID::class.java),
            email = getString("email"),
            passwordHash = getString("password_hash"),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            firstName = getString("first_name"),
            lastName = getString("last_name"),
            phone = getString("phone"),
            customerType = getString("customer_type"),
            deliveryCompany = getString("delivery_company"),
            deliveryAddressLine1 = getString("delivery_address_line1"),
            deliveryAddressLine2 = getString("delivery_address_line2"),
            deliveryTownOrCity = getString("delivery_town_or_city"),
            deliveryPostcode = getString("delivery_postcode"),
            deliveryCountry = getString("delivery_country"),
            sameAsDeliveryAddress = getBoolean("same_as_delivery_address"),
            invoiceCompany = getString("invoice_company"),
            invoiceAddressLine1 = getString("invoice_address_line1"),
            invoiceAddressLine2 = getString("invoice_address_line2"),
            invoiceTownOrCity = getString("invoice_town_or_city"),
            invoicePostcode = getString("invoice_postcode"),
            invoiceCountry = getString("invoice_country"),
            vatNumber = getString("vat_number"),
            projectNotes = getString("project_notes")
        )
}
