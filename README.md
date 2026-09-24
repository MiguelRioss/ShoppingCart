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
- [Multi-origin Shipping](docs/MULTI_ORIGIN_SHIPPING.md)
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

## Shipping Provider

ShoppingCart owns the runtime profile and carrier credentials. Configure them in
the application environment, never in the middleware library:

```env
FEDEX_MODE=SANDBOX
FEDEX_TEST_CLIENT_ID=...
FEDEX_TEST_CLIENT_SECRET=...
FEDEX_TEST_ACCOUNT_NUMBER=...
FEDEX_TEST_ACCOUNT_COUNTRY_CODE=GB
```

At application startup, `ShippingProviderConfig` reads those values and the server
constructs the carrier-neutral provider:

```kotlin
val config = ShippingProviderConfig.load()

val shippingProvider =
    when (config.mode) {
        ShippingMode.OFFLINE -> ShippingProvider.offline()
        ShippingMode.SANDBOX,
        ShippingMode.PRODUCTION ->
            ShippingProvider.login(
                type = ShipmentProviderType.FEDEX,
                account = requireNotNull(config.account),
                credentials = requireNotNull(config.credentials),
                environment = requireNotNull(config.providerEnvironment)
            )
    }
```

Application services then depend only on the two carrier-neutral operations:

```kotlin
val quotes = shippingProvider.getQuotes(rateRequest)
val shipment = shippingProvider.prepareShipping(shipmentRequest)
```

`getQuotes` is used before checkout. `prepareShipping` must be called only after
payment confirmation and once per supplier origin.

## Current Scope

The checkout calculates FedEx shipping through the carrier-neutral middleware,
groups products by supplier origin, and adds the selected shipping totals to one
Stripe payment. Post-payment label creation is documented but is not yet connected
to the Stripe webhook.
