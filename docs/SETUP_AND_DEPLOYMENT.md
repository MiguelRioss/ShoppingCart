# Setup And Deployment

## Requirements

- JDK 22
- Gradle wrapper included in the repository
- Optional: Docker
- Optional for online mode: PostgreSQL database
- Required for checkout: Stripe secret key

## Run Tests

Windows:

```powershell
.\gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
```

## Run Locally

Offline mode uses in-memory repositories. Data is lost when the app restarts.

```powershell
.\gradlew.bat run
```

Default URL:

```text
http://localhost:8080
```

## Environment Variables

| Name | Required | Notes |
| --- | --- | --- |
| `PORT` | No | Defaults to `8080`. |
| `APP_MODE` | No | Use `online` for PostgreSQL. Any other value uses offline in-memory mode. |
| `DATABASE_URL` | Required when `APP_MODE=online` | Supports Render-style `postgresql://...` URLs and JDBC URLs. |
| `DATABASE_USER` | No | Used only when `DATABASE_URL` is not a `postgresql://...` URL. |
| `DATABASE_PASSWORD` | No | Used only when `DATABASE_URL` is not a `postgresql://...` URL. |
| `STRIPE_SECRET_KEY` | Required for checkout | Stripe API secret key. |
| `STRIPE_CHECKOUT_API_URL` | No | Defaults to `https://api.stripe.com/v1/checkout/sessions`. |

Local `.env` files are supported by `src/config/Environment.kt`.

Example `.env`:

```text
APP_MODE=offline
PORT=8080
STRIPE_SECRET_KEY=sk_test_your_key
```

## Docker

Build:

```powershell
docker build -t shopping-cart-api .
```

Run:

```powershell
docker run --rm -p 8080:8080 -e STRIPE_SECRET_KEY=sk_test_your_key shopping-cart-api
```

## Render

The Render service is configured in:

```text
render.yaml
```

Current service summary:

```yaml
services:
  - type: web
    name: shopping-cart
    runtime: docker
    plan: free
    dockerfilePath: ./Dockerfile
    envVars:
      - key: APP_MODE
        value: online
      - key: PORT
        value: 8080
```

Set these secrets in Render:

```text
DATABASE_URL
STRIPE_SECRET_KEY
```

Do not commit real secrets to the repository.
