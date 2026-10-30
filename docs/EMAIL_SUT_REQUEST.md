# Real-order email SUT request

Prepare a payment-confirmation request from a saved paid order:

```powershell
.\gradlew.bat prepareEmailSut -PorderId=YOUR_ORDER_UUID --console=plain
```

The request is written to `.cache/email-sut/payment-confirmation.json`, with a `.sources.txt` provenance file beside it. The private request is ignored by Git and contains no API key. It includes a real generated payment-summary PDF as Base64 attachment bytes; it does not use the generic API-test PDF.

The tool reads saved order names, quantities, prices, payment amount, currency and customer/account information. Older orders missing image or lead-time snapshots are enriched from the current product catalogue in memory. The provenance file identifies those current-catalogue fields. No order or customer record is updated.

The request uses a separate `sut:` event/idempotency key so testing does not consume the production payment event. Reusing the same request returns the saved send result without another email. A deliberate new preview can use `-Prevision=YOUR_REVISION`.

From EMAIL_SENDER, send the prepared request to the deployed V2 API:

```powershell
npm run v2:api -- --route send --request-file C:\Projects\ShoppingCart\.cache\email-sut\payment-confirmation.json
```

EMAIL_SENDER's temporary recipient override delivers to Miguel's Gmail. The intended customer, product information and saved order reference remain in the request. SMTP acceptance and the deduplication/history check are reported by the command. The attachment is a payment summary, not a VAT or customs invoice.
