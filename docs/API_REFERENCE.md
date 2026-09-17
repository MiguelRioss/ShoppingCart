# ShoppingCart API Reference

Base URLs:

```text
Local:  http://localhost:8080
Render: https://shoppingcart-4ohf.onrender.com
```

All request and response bodies are JSON.

## Authentication

Send authenticated requests with:

```http
Authorization: Bearer <token>
```

Tokens are returned by `POST /register` and `POST /login`.

## Register

```http
POST /register
Content-Type: application/json
```

Creates a user and returns an auth token.

Request:

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
  "sameAsDeliveryAddress": true,
  "vatNumber": "PT123456789",
  "projectNotes": "Please deliver to reception.",
  "sessionId": "browser-session-id"
}
```

Accepted `customerType` values:

```text
private_customer
private
private customer
business
```

`sessionId` is optional. If supplied and a guest cart exists, the backend associates that cart with the new user.

Success `201`:

```json
{
  "token": "auth-token",
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
  "password": "password-123",
  "sessionId": "browser-session-id"
}
```

`sessionId` is optional. If supplied and a guest cart exists, the backend associates that cart with the logged-in user.

Success `200`:

```json
{
  "token": "auth-token",
  "userId": "user-uuid",
  "expiresAt": "2026-08-12T14:29:44"
}
```

## Auth Status

```http
GET /auth/status
Authorization: Bearer <token>
```

Success `200`:

```json
{
  "authenticated": true,
  "userId": "user-uuid",
  "email": "buyer@example.com"
}
```

Missing or invalid token returns `401`.

## Get Client Info

```http
GET /client/info
Authorization: Bearer <token>
```

Success `200`:

```json
{
  "id": "user-uuid",
  "email": "buyer@example.com",
  "createdAt": "2026-08-12T14:29:44",
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+351 912 345 678",
  "customerType": "PrivateCustomer",
  "deliveryCompany": "Example Ltd",
  "deliveryAddressLine1": "Street and house number",
  "deliveryAddressLine2": "Apartment, suite, unit",
  "deliveryTownOrCity": "Lisbon",
  "deliveryPostcode": "1000-001",
  "deliveryCountry": "Portugal",
  "sameAsDeliveryAddress": true,
  "invoiceCompany": "Example Ltd",
  "invoiceAddressLine1": "Street and house number",
  "invoiceAddressLine2": "Apartment, suite, unit",
  "invoiceTownOrCity": "Lisbon",
  "invoicePostcode": "1000-001",
  "invoiceCountry": "Portugal",
  "vatNumber": "PT123456789",
  "projectNotes": "Please deliver to reception."
}
```

## Update Account

```http
PUT /account
Authorization: Bearer <token>
Content-Type: application/json
```

Updates the authenticated user's account. Fields are optional; omitted fields keep their current value.

Request:

```json
{
  "firstName": "Jane",
  "lastName": "Postman",
  "phone": "+351 912 000 111",
  "customerType": "business",
  "deliveryAddress": {
    "addressLine1": "Updated delivery street",
    "townOrCity": "Porto",
    "postcode": "4000-001",
    "country": "Portugal"
  },
  "sameAsDeliveryAddress": true,
  "projectNotes": "Updated from API"
}
```

Success `200` returns the updated client info response.

## Delete Account

```http
DELETE /account
Authorization: Bearer <token>
```

Deletes the authenticated user account.

Success `200`:

```json
{}
```

## Get Products

```http
GET /products
```

Returns purchasable products from the external De Ferranti catalog.

Success `200` returns an array of product objects from the catalog.

## Get Product By ID

```http
GET /products/{id}
```

Example:

```http
GET /products/9278
```

Success `200` returns one product object.

## Save Cart

```http
POST /cart
Content-Type: application/json
Authorization: Bearer <token>
```

The `Authorization` header is optional. Send it when the user is logged in.

Request:

```json
{
  "sessionId": "browser-session-id",
  "products": [
    {
      "productId": 9278,
      "quantityM2": 0.5
    }
  ]
}
```

Rules:

- `sessionId` is required.
- `products` must contain at least one item.
- `productId` is required.
- `quantityM2` must be greater than `0`.
- Saving the same `sessionId` again replaces the cart products.

Success `201`:

```json
{
  "id": "cart-uuid",
  "userId": "user-uuid",
  "sessionId": "browser-session-id",
  "dateTime": "2026-08-16T15:30",
  "products": [
    {
      "productId": 9278,
      "squareMeters": 0.5,
      "amountBoxes": 1,
      "totalPricePerProduct": "75.00"
    }
  ]
}
```

## Get Cart

```http
GET /cart?sessionId=<session-id>
Authorization: Bearer <token>
```

Both `sessionId` and `Authorization` are optional, but at least one must identify an existing cart.

Lookup order:

1. If `sessionId` is supplied, the backend tries that cart first.
2. If no session cart exists and a bearer token is supplied, the backend tries the authenticated user's cart.

Success `200` returns the cart response.

Missing cart returns `404`.

## Clear Cart

```http
POST /cart/clear
Content-Type: application/json
```

Request:

```json
{
  "sessionId": "browser-session-id"
}
```

Success `200`:

```json
{
  "message": "Shopping cart cleared",
  "sessionId": "browser-session-id"
}
```

## Create Checkout

```http
POST /checkout
Content-Type: application/json
```

Creates a Stripe Checkout session for the cart.

Request without shipping charge:

```json
{
  "sessionId": "browser-session-id",
  "successUrl": "https://example.com/success",
  "cancelUrl": "https://example.com/cancel",
  "customerEmail": "buyer@example.com",
  "shippingCharge": null
}
```

Request with a manually supplied shipping charge:

```json
{
  "sessionId": "browser-session-id",
  "successUrl": "https://example.com/success",
  "cancelUrl": "https://example.com/cancel",
  "customerEmail": "buyer@example.com",
  "shippingCharge": {
    "serviceType": "STANDARD_DELIVERY",
    "serviceName": "Standard delivery",
    "amountTotal": "25.00",
    "currency": "eur",
    "estimatedDeliveryDate": "unavailable",
    "originalAmount": "25.00",
    "originalCurrency": "EUR"
  }
}
```

Success `201`:

```json
{
  "id": "cs_test_123",
  "provider": "stripe",
  "status": "created",
  "url": "https://checkout.stripe.com/c/pay/cs_test_123"
}
```

## Error Shape

Errors usually return:

```json
{
  "message": "Human readable message",
  "description": "More specific explanation"
}
```

Common status codes:

```text
400 Bad Request
401 Unauthorized
404 Not Found
409 Conflict
500 Internal Server Error
502 Bad Gateway
```
