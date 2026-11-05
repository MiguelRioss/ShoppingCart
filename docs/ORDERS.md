# Orders

`POST /checkout` saves an immutable order snapshot after Stripe creates a session and before returning its payment link. The snapshot contains customer email, delivery address, cart quantities, resolved product prices and shipping. Its initial status is `PENDING_PAYMENT`.

`GET /orders` requires the existing bearer token and returns only the authenticated user's orders.

`POST /webhooks/stripe` verifies the raw request using `Stripe-Signature` and `STRIPE_WEBHOOK_SECRET`, with a five-minute timestamp tolerance. Configure Stripe to send `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired` to this endpoint. Completed sessions remain pending until `payment_status` is `paid`. The unique Stripe session ID prevents duplicate orders, and failures never downgrade a paid order. Missing orders return 503 so Stripe retries a possible race with checkout persistence.

## Enable persistence

Set `APP_MODE=online` and configure `DATABASE_URL`, then restart the backend. Startup creates the `orders` table and its user index. Offline mode intentionally stores orders only in memory. The schema change does not backfill previous purchases.

## Enable payment confirmation

Set `STRIPE_WEBHOOK_SECRET` to the signing secret for the configured webhook endpoint and restart the backend. For local testing use Stripe CLI forwarding: `stripe listen --forward-to localhost:8080/webhooks/stripe`, and use the secret printed by that command. For deployment register the public backend's `/webhooks/stripe` endpoint in Stripe and use its own endpoint secret. Match the endpoint's Stripe test/live mode to the checkout key.

The browser success page does not mark orders paid. Closing the browser after paying does not prevent a configured webhook from recording payment. Existing checkout sessions created before this change have no order snapshot and cannot be reconstructed by this endpoint.

## TODO: tax treatment for invoices

The order currently saves `taxAmount = 0.00` with `taxCalculation = temporary_zero`. Before issuing final invoices, determine the applicable VAT treatment from the selling entity, dispatch country, destination, customer type and relevant registrations. Save the applied rate, amount and legal basis for any exemption in the immutable order record, and render those saved values in the invoice. The temporary zero must not be interpreted as a confirmed exemption.

