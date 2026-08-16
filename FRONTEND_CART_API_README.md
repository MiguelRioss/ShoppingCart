# Frontend User And Cart API Guide

API base URL:

```text
https://shoppingcart-4ohf.onrender.com
```

This guide is only about user login and cart saving.

## Frontend State

The frontend should keep two values:

```text
token
sessionId
```

Use `token` for logged-in requests.

Use `sessionId` for cart save and cart clear.

Example `sessionId`:

```text
browser-22b0076c-5a1a-438a-9fdb-1c9efec7276e
```

Create this once in the browser and keep it in local storage.

## Register

```http
POST /register
Content-Type: application/json
```

Success:

```http
201
```

Response:

```json
{
  "token": "token-value",
  "userId": "user-uuid",
  "expiresAt": "date-time"
}
```

Frontend should save the returned `token`.

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

Success:

```http
200
```

Response:

```json
{
  "token": "token-value",
  "userId": "user-uuid",
  "expiresAt": "date-time"
}
```

Frontend should save the returned `token`.

## Check Login

```http
GET /auth/status
Authorization: Bearer <token>
```

Use this to verify the user is logged in.

Success:

```http
200
```

## Get Client Info

```http
GET /client/info
Authorization: Bearer <token>
```

Use this to get the logged-in user's profile data.

## Save Cart

```http
POST /cart
Content-Type: application/json
Authorization: Bearer <token>
```

The `Authorization` header is optional for saving, but if the user is logged in, send it.

Request:

```json
{
  "sessionId": "browser-22b0076c-5a1a-438a-9fdb-1c9efec7276e",
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
- `products` must have at least one item.
- `productId` is required.
- `quantityM2` must be greater than `0`.

## Save Cart Behavior

If no cart exists for that `sessionId`, the backend creates one.

If a cart already exists for that `sessionId`, the backend updates the existing cart.

Saving again with the same `sessionId` replaces the cart products with the new `products` array.

Success:

```http
201
```

Response:

```json
{
  "id": "cart-uuid",
  "userId": "user-uuid",
  "sessionId": "browser-22b0076c-5a1a-438a-9fdb-1c9efec7276e",
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
GET /cart
Authorization: Bearer <token>
```

This gets the logged-in user's cart.

The frontend must send the token.

Success:

```http
200
```

If the user has no saved cart:

```http
404
```

## Clear Cart

```http
POST /cart/clear
Content-Type: application/json
```

Request:

```json
{
  "sessionId": "browser-22b0076c-5a1a-438a-9fdb-1c9efec7276e"
}
```

This clears the cart associated with that `sessionId`.

Success:

```http
200
```

Response:

```json
{
  "message": "Shopping cart cleared",
  "sessionId": "browser-22b0076c-5a1a-438a-9fdb-1c9efec7276e"
}
```

If there is no cart for that `sessionId`:

```http
404
```

Response:

```json
{
  "message": "Shopping cart not found for sessionId",
  "description": "Shopping cart not found for sessionId"
}
```

## Error Response Shape

Errors usually look like:

```json
{
  "message": "Error message",
  "description": "More specific explanation"
}
```

Use the HTTP status code to decide how to handle the error.

Common statuses:

```text
400 Bad Request
401 Unauthorized
404 Not Found
409 Conflict
500 Internal Server Error
```

## Frontend Checklist

1. Create and store one `sessionId`.
2. Register or login.
3. Save the returned `token`.
4. Send `Authorization: Bearer <token>` when the user is logged in.
5. Send `sessionId` when saving or clearing cart.
6. Use `POST /cart` to save or update the cart.
7. Use `GET /cart` to load the logged-in user's cart.
8. Use `POST /cart/clear` to clear the cart for the current `sessionId`.
