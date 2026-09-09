package http

import dto.auth.CustomerType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegisterUserRequestTest {
    @Test
    fun `creates register user request from checkout details json`() {
        val request = parseRegisterUserRequest(
            """
            {
              "firstName": "Jane",
              "lastName": "Smith",
              "email": "jane.smith@example.com",
              "password": "password-123",
              "phone": "+351 912 345 678",
              "customerType": "private_customer",
              "deliveryAddress": {
                "company": null,
                "addressLine1": "Street and house number",
                "addressLine2": "Apartment, suite, unit",
                "townOrCity": "Lisbon",
                "postcode": "1000-001",
                "country": "Portugal"
              },
              "sameAsDeliveryAddress": true,
              "vatNumber": "PT123456789",
              "projectNotes": "Please deliver to reception."
            }
            """.trimIndent()
        )

        assertEquals("Jane", request.firstName)
        assertEquals("Smith", request.lastName)
        assertEquals("jane.smith@example.com", request.email)
        assertEquals("+351 912 345 678", request.phone)
        assertEquals(CustomerType.PrivateCustomer, request.customerType)
        assertEquals("Lisbon", request.deliveryTownOrCity)
        assertEquals(request.deliveryAddressLine1, request.invoiceAddressLine1)
        assertEquals("PT123456789", request.vatNumber)
        assertEquals("Please deliver to reception.", request.projectNotes)
    }

    @Test
    fun `creates different invoice address when not same as delivery address`() {
        val request = parseRegisterUserRequest(
            """
            {
              "firstName": "Jane",
              "lastName": "Smith",
              "email": "jane.smith@example.com",
              "password": "password-123",
              "phone": "+351 912 345 678",
              "customerType": "business",
              "deliveryAddress": {
                "addressLine1": "Delivery street",
                "townOrCity": "Lisbon",
                "postcode": "1000-001",
                "country": "Portugal"
              },
              "sameAsDeliveryAddress": false,
              "invoiceAddress": {
                "company": "Example Ltd",
                "addressLine1": "Invoice street",
                "townOrCity": "Porto",
                "postcode": "4000-001",
                "country": "Portugal"
              }
            }
            """.trimIndent()
        )

        assertEquals(CustomerType.Business, request.customerType)
        assertEquals("Delivery street", request.deliveryAddressLine1)
        assertEquals("Example Ltd", request.invoiceCompany)
        assertEquals("Invoice street", request.invoiceAddressLine1)
    }

    @Test
    fun `register user request can skip optional fields`() {
        val request = parseRegisterUserRequest(
            """
            {
              "firstName": "Jane",
              "lastName": "Smith",
              "email": "jane.smith@example.com",
              "password": "password-123",
              "phone": "+351 912 345 678",
              "customerType": "private_customer",
              "deliveryAddress": {
                "addressLine1": "Street and house number",
                "townOrCity": "Lisbon",
                "postcode": "1000-001",
                "country": "Portugal"
              }
            }
            """.trimIndent()
        )

        assertEquals(null, request.deliveryCompany)
        assertEquals(null, request.deliveryAddressLine2)
        assertEquals(true, request.sameAsDeliveryAddress)
        assertEquals(request.deliveryAddressLine1, request.invoiceAddressLine1)
        assertEquals(null, request.vatNumber)
        assertEquals(null, request.projectNotes)
    }

    @Test
    fun `fails for invalid register json`() {
        assertFailsWith<IllegalArgumentException> {
            parseRegisterUserRequest("not-json")
        }
    }
}
