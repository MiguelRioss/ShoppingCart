# ShoppingCart

Kotlin HTTP API for user accounts, product lookup, shopping cart storage, and Stripe Checkout session creation.

Base URLs:

```text
Local:  http://localhost:8080
Render: https://shoppingcart-4ohf.onrender.com
```

## Documentation

- [API Reference](docs/API_REFERENCE.md)
- [Integration Guide](docs/INTEGRATION_GUIDE.md)
- [Setup And Deployment](docs/SETUP_AND_DEPLOYMENT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Kotlin Decompiler Stubs](docs/KOTLIN_DECOMPILER_STUBS.md)

## Useful Files

- [Postman collection](postman-shoppingcart-render.postman_collection.json)
- [Render config](render.yaml)
- [Frontend tester](frontend/login-tester.html)

## Quick Start

Run tests:

```powershell
.\gradlew.bat test
```

Run locally:

```powershell
.\gradlew.bat run
```

The API starts on:

```text
http://localhost:8080
```

## Current Scope

This project does not contain FedEx-specific shipping code. Checkout supports an optional generic `shippingCharge` object, so another application can calculate shipping separately and pass the final charge into `POST /checkout`.
