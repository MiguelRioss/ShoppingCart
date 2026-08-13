# ShoppingCart API Routes

Base URL:

```text
http://localhost:8080
```

## Register

```http
POST /register
Content-Type: application/json
```

Request fields:

| Field | Required | Notes |
| --- | --- | --- |
| `firstName` | Yes | Customer first name |
| `lastName` | Yes | Customer last name |
| `email` | Yes | Used for login |
| `password` | Yes | Used for login |
| `phone` | Yes | Customer phone number |
| `customerType` | Yes | Accepted values: `private_customer`, `private`, `business` |
| `deliveryAddress` | Yes | Object with delivery address fields |
| `deliveryAddress.company` | No | Company name |
| `deliveryAddress.addressLine1` | Yes | First address line |
| `deliveryAddress.addressLine2` | No | Second address line |
| `deliveryAddress.townOrCity` | Yes | Town or city |
| `deliveryAddress.postcode` | Yes | Postal code |
| `deliveryAddress.country` | Yes | Country |
| `sameAsDeliveryAddress` | No | Defaults to `true` |
| `invoiceAddress` | Only when `sameAsDeliveryAddress` is `false` | Object with invoice address fields |
| `invoiceAddress.company` | No | Company name |
| `invoiceAddress.addressLine1` | Yes, when `invoiceAddress` is required | First address line |
| `invoiceAddress.addressLine2` | No | Second address line |
| `invoiceAddress.townOrCity` | Yes, when `invoiceAddress` is required | Town or city |
| `invoiceAddress.postcode` | Yes, when `invoiceAddress` is required | Postal code |
| `invoiceAddress.country` | Yes, when `invoiceAddress` is required | Country |
| `vatNumber` | No | VAT number |
| `projectNotes` | No | Notes about the customer's project |

Request example:

```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "buyer@example.com",
  "password": "password-123",
  "phone": "+351 912 345 678",
  "customerType": "private_customer",
  "deliveryAddress": {
    "company": "Example Ltd",
    "addressLine1": "Street and house number",
    "addressLine2": "Apartment, suite, unit",
    "townOrCity": "Lisbon",
    "postcode": "1000-001",
    "country": "Portugal"
  },
  "sameAsDeliveryAddress": false,
  "invoiceAddress": {
    "company": "Example Ltd",
    "addressLine1": "Invoice street",
    "addressLine2": "Floor 2",
    "townOrCity": "Porto",
    "postcode": "4000-001",
    "country": "Portugal"
  },
  "vatNumber": "PT123456789",
  "projectNotes": "Please deliver to reception."
}
```

Response `201`:

```json
{
  "token": "uuid-token",
  "userId": "user-uuid",
  "expiresAt": "2026-08-12T14:29:44"
}
```

## Login

```http
POST /login
Content-Type: application/json
```

Request:

```json
{
  "email": "buyer@example.com",
  "password": "password-123"
}
```

Response `200`:

```json
{
  "token": "uuid-token",
  "userId": "user-uuid",
  "expiresAt": "2026-08-12T14:29:44"
}
```

## Check Auth

```http
GET /auth/status
Authorization: Bearer uuid-token
```

Response `200`:

```json
{
  "authenticated": true,
  "userId": "user-uuid",
  "email": "buyer@example.com"
}
```

## Get Client Info

```http
GET /client/info
Authorization: Bearer uuid-token
```

Response `200`:

```json
{
  "userId": "user-uuid",
  "email": "buyer@example.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+351 912 345 678",
  "customerType": "PrivateCustomer",
  "deliveryAddress": {
    "company": "Example Ltd",
    "addressLine1": "Street and house number",
    "addressLine2": "Apartment, suite, unit",
    "townOrCity": "Lisbon",
    "postcode": "1000-001",
    "country": "Portugal"
  },
  "sameAsDeliveryAddress": true,
  "invoiceAddress": {
    "company": "Example Ltd",
    "addressLine1": "Invoice street",
    "addressLine2": "Floor 2",
    "townOrCity": "Porto",
    "postcode": "4000-001",
    "country": "Portugal"
  },
  "vatNumber": "PT123456789",
  "projectNotes": "Please deliver to reception."
}
```

## Get Cart

```http
GET /cart
Authorization: Bearer uuid-token
```

Response `200`:

```json
{
  "id": "cart-uuid",
  "userId": "user-uuid",
  "dateTime": "2026-08-07T13:45:00",
  "products": [
    {
      "productId": 1864,
      "squareMeters": 12.5,
      "amountBoxes": 3,
      "totalPricePerProduct": "249.99"
    }
  ]
}
```

## Common Errors

Response `400`:

```json
{
  "error": "invalid_json_request_body",
  "message": "Invalid JSON request body"
}
```

Response `401`:

```json
{
  "error": "unauthorized",
  "message": "Invalid credentials"
}
```

Response `404`:

```json
{
  "error": "not_found",
  "message": "Shopping cart not found"
}
```

Response `409`:

```json
{
  "error": "conflict",
  "message": "User already exists"
}
```
