# Customer journey without a browser

From ShoppingCart:

```powershell
.\scripts\Test-ClientJourney.ps1
```

To include real Neon email history:

```powershell
.\scripts\Test-ClientJourney.ps1 -Neon
```

The direct Gradle command is `./gradlew clientJourneyTest` (Windows: `.\gradlew.bat clientJourneyTest`). The test is tagged `journey` and deliberately excluded from the normal unit-test task because it needs the EMAIL_SENDER checkout and Node.

## Requirements

- Node and the normal ShoppingCart Java/Gradle setup.
- Current EMAIL_SENDER repository next to ShoppingCart, with dependencies installed (`npm ci` there). An alternate path can be supplied with `-EmailSenderRepo C:\path\to\EMAIL_SENDER` or the `EMAIL_SENDER_REPO` environment variable.
- For `-Neon`, EMAIL_SENDER's `.env` must contain `EMAIL_DATABASE_URL`, or that variable must be exported. The optional Neon mode writes temporary, uniquely marked email rows and removes them at the end. It never changes real orders, carts or customers.

No running servers, UI, Stripe account or SMTP credentials are needed for the default mode. The harness starts both applications on temporary loopback ports and stops them afterward.

## What is exercised

1. Register and log in through ShoppingCart HTTP routes.
2. Save and retrieve two independent carts for one user.
3. Create a checkout through the real Stripe adapter, using a local fake Stripe endpoint and deterministic shipping.
4. Reject an unsigned webhook; send nothing for an unpaid checkout.
5. Process signed payment confirmation, persist the paid order, clear only the purchased cart and preserve the other cart.
6. Call the real EMAIL_SENDER V2 HTTP handler, render the payment-confirmation template, generate a real payment-summary PDF and capture its SMTP submission.
7. Check the recipient override, inline logo, valid PDF bytes and saved product lead time—even after the catalogue estimate changes.
8. Replay the paid event: one captured email and one accepted history record.
9. Exercise delayed payment and expired checkout: delayed payment sends only after success; expired checkout retains its cart and sends nothing.
10. Explicitly simulate the nine other email actions through the protected admin route: commercial invoice confirmation, purchase order, packing list, delivery note, revised despatch date, despatch, delivery, thank-you and incomplete checkout. Check the four real PDF generators, all recipient overrides and repeat-request suppression.

The later fulfilment actions are invoked explicitly by the test. This does not add automatic supplier/shipping/delivery triggers to production.

## Boundaries

ShoppingCart uses real services, handlers and local HTTP with in-memory order/user/cart repositories. EMAIL_SENDER uses its real renderer, SMTP adapter and delivery-control code. Default email history is an injected test store; `-Neon` exercises its real PostgreSQL store. External Stripe, shipping and SMTP are simulated: no card is charged, shipment booked or actual email delivered.

Passing this test validates the application flow, not Render environment settings, Stripe's configured webhook endpoint or mailbox credentials/deliverability. Those still need a deployed test checkout.

The console prints each completed stage. The full report is `build/reports/tests/clientJourneyTest/index.html`. A failure exits with an error and still attempts temporary-row and server cleanup.
