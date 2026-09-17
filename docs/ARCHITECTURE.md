# Architecture

## Purpose

This project exposes a Kotlin HTTP API for:

- User registration and login
- Auth token status
- Account profile management
- Product catalog reads
- Shopping cart save, load, and clear
- Stripe Checkout session creation

FedEx-specific shipping code has been removed from this project.

## Entry Point

Application entry:

```text
src/Main.kt
```

Server composition:

```text
src/server/ShoppingCartServer.kt
```

`ShoppingCartServer` creates repositories, services, route handlers, and the HTTP server.

## Route Layer

Routes are registered in `ShoppingCartServer`:

```text
POST   /register
POST   /login
GET    /auth/status
PUT    /account
DELETE /account
GET    /client/info
GET    /products
GET    /products/:id
GET    /cart
POST   /cart
POST   /cart/clear
POST   /checkout
```

Important route classes:

```text
src/http/auth
src/http/cart
src/http/checkout
src/http/client
src/http/product
```

## DTO Layer

DTOs parse JSON request bodies and serialize JSON responses.

Important DTO folders:

```text
src/dto/auth
src/dto/cart
src/dto/checkout
src/dto/user
```

## Service Layer

Core business logic lives in:

```text
src/services/auth
src/services/cart
src/services/checkout
src/services/user
```

Examples:

- `AuthManager` handles register/login/token validation.
- `ShoppingCartManager` handles cart create, update, fetch, clear, and user association.
- `CheckoutManager` validates the cart and creates a Stripe Checkout session.
- `UserManager` handles account reads, updates, and deletion.

## Data Layer

Repository interfaces:

```text
src/db/UserRepository.kt
src/db/AuthTokenRepository.kt
src/db/ShoppingCartRepository.kt
```

Offline implementations:

```text
src/db/offline
```

PostgreSQL implementations:

```text
src/db/postgres
```

The app chooses the data layer from `APP_MODE`:

- `APP_MODE=online`: PostgreSQL
- Anything else: in-memory repositories

## Product Catalog

Product data comes from the external De Ferranti catalog:

```text
https://cms.deferranti.com/wp-json/custom/v1
```

The product catalog integration is in:

```text
src/productDatabaseAccessLayer/ProductCatalogDataSource.kt
```

The cart uses product catalog fields to calculate:

- boxes required
- total product price

## Checkout

Checkout is handled through Stripe:

```text
src/services/checkout/payment/stripe
```

`POST /checkout` creates a Stripe Checkout session using:

- cart line items
- optional shipping line item
- success URL
- cancel URL
- customer email

## Error Handling

HTTP errors are centralized in:

```text
src/http/HttpError.kt
```

Service-level errors use:

```text
src/services/common/ServiceException.kt
```
