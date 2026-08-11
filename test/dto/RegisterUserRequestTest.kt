package dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegisterUserRequestTest {
    @Test
    fun `creates register user request from checkout details json`() {
        val request = RegisterUserRequest.fromJson(
            """
            {
              "firstName": "Jane",
              "lastName": "Smith",
              "email": "jane.smith@example.com",
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
        assertEquals("Lisbon", request.deliveryAddress?.townOrCity)
        assertEquals(request.deliveryAddress, request.invoiceAddress)
        assertEquals("PT123456789", request.vatNumber)
        assertEquals("Please deliver to reception.", request.projectNotes)
    }

    @Test
    fun `creates different invoice address when not same as delivery address`() {
        val request = RegisterUserRequest.fromJson(
            """
            {
              "firstName": "Jane",
              "lastName": "Smith",
              "email": "jane.smith@example.com",
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
        assertEquals("Delivery street", request.deliveryAddress?.addressLine1)
        assertEquals("Example Ltd", request.invoiceAddress?.company)
        assertEquals("Invoice street", request.invoiceAddress?.addressLine1)
    }

    @Test
    fun `register user request can skip optional fields`() {
        val request = RegisterUserRequest.fromJson(
            """
            {
              "firstName": "Jane",
              "lastName": "Smith",
              "email": "jane.smith@example.com",
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

        assertEquals(null, request.deliveryAddress?.company)
        assertEquals(null, request.deliveryAddress?.addressLine2)
        assertEquals(true, request.sameAsDeliveryAddress)
        assertEquals(request.deliveryAddress, request.invoiceAddress)
        assertEquals(null, request.vatNumber)
        assertEquals(null, request.projectNotes)
    }

    @Test
    fun `register user request requires personal details`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RegisterUserRequest.fromJson(
                """
                {
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
        }

        assertEquals("First name is required", error.message)
    }

    @Test
    fun `register user request requires last name`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RegisterUserRequest.fromJson(
                """
                {
                  "firstName": "Jane",
                  "email": "jane.smith@example.com",
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
        }

        assertEquals("Last name is required", error.message)
    }

    @Test
    fun `register user request requires delivery address fields`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RegisterUserRequest.fromJson(
                """
                {
                  "firstName": "Jane",
                  "lastName": "Smith",
                  "email": "jane.smith@example.com",
                  "phone": "+351 912 345 678",
                  "customerType": "private_customer",
                  "deliveryAddress": {
                    "townOrCity": "Lisbon",
                    "postcode": "1000-001",
                    "country": "Portugal"
                  }
                }
                """.trimIndent()
            )
        }

        assertEquals("Delivery address line 1 is required", error.message)
    }

    @Test
    fun `register user request requires invoice address when not same as delivery address`() {
        val error = assertFailsWith<IllegalArgumentException> {
            RegisterUserRequest.fromJson(
                """
                {
                  "firstName": "Jane",
                  "lastName": "Smith",
                  "email": "jane.smith@example.com",
                  "phone": "+351 912 345 678",
                  "customerType": "private_customer",
                  "deliveryAddress": {
                    "addressLine1": "Street and house number",
                    "townOrCity": "Lisbon",
                    "postcode": "1000-001",
                    "country": "Portugal"
                  },
                  "sameAsDeliveryAddress": false
                }
                """.trimIndent()
            )
        }

        assertEquals("Invoice address is required", error.message)
    }

    @Test
    fun `fails for invalid register json`() {
        assertFailsWith<IllegalArgumentException> {
            RegisterUserRequest.fromJson("not-json")
        }
    }
}
