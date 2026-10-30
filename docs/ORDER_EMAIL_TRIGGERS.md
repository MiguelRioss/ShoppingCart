# Order Email Triggers

These rules come from Alvaro de Ferranti's ShoppingCart email brief. The HTML templates and automation manifest are in `C:/Projects/EMAIL_SENDER/layouts/DEFERRANTI/`.

## When each email should be sent

| Email | Recipient | Trigger | Attachment |
| --- | --- | --- | --- |
| Order confirmation | Customer | Payment is verified as successful. Opening checkout or loading the success page is not sufficient. | Invoice PDF |
| Purchase order | Supplier | The customer order is confirmed and that supplier's purchase order has been generated. | Purchase Order PDF |
| Incomplete checkout | Customer | Checkout remains unpaid after 12–24 hours. The exact delay still needs deciding. Recheck payment status immediately before sending. | None |
| Packing list | Supplier | The packing list is generated and goods are approaching despatch. | Packing List PDF |
| Delivery note | Supplier | The delivery note is generated before courier collection. | Delivery Note PDF |
| Revised despatch date | Customer | The new expected despatch date is later than the last date communicated to the customer. Ordinary order edits must not resend it. | None |
| Goods despatched | Customer | Despatch or collection is confirmed and tracking information is available. Creating a label or entering tracking alone must not falsely imply collection. | None |
| Delivery confirmation | Customer | FedEx or the courier confirms delivery. Include the requested 24-hour inspection notice. | None |
| Thank you and 10% discount | Customer | Seven days after confirmed delivery. The discount is unconditional; marketing opt-in remains a separate positive action. | None |

## Shared sending rules

- Populate customer, product, quantity, price and delivery information from the relevant saved order data wherever available.
- Reuse the existing PDF generated for that specific order and document version. Do not generate a separate version solely for the email.
- Send supplier emails only to the relevant supplier, with that supplier's products and documents. Handle multiple suppliers and shipments independently.
- Log every send against the order, including recipient, template, trigger/document version, send time, delivery-provider message ID and idempotency key.
- Prevent duplicate sends during repeated webhooks, order saves and job retries. Use durable jobs and atomic deduplication rather than process memory.
- Suppress incomplete-checkout reminders once payment succeeds.
- Store the original expected despatch date and the last date communicated to the customer. A revised-date email is sent only for a later date; an optional `despatchUpdateNote` can explain why.
- Use a real, single-use 10% discount code with supplied terms. Do not make the discount conditional on marketing consent or treat an email open as consent.

## Current integration status

The nine HTML templates, product-card rendering, subjects, variable requirements and trigger metadata have been prepared in EMAIL_SENDER. Automatic sending is not activated yet.

ShoppingCart verifies Stripe payment webhooks and records paid orders. The remaining email integration must connect those events and the fulfilment/delivery lifecycle to durable email jobs, order-specific document storage, sending logs and duplicate-send protection.

The existing commercial invoice PDF generator is available, but automatic generation/storage/attachment after payment is not yet wired into this email workflow. Confirm whether it also meets the customer billing invoice requirements before using it for that purpose.

## Related files

- `C:/Projects/EMAIL_SENDER/layouts/DEFERRANTI/manifest.json` — subjects, variables, attachment requirements and proposed trigger metadata.
- `C:/Projects/EMAIL_SENDER/layouts/DEFERRANTI/README.md` — renderer usage and integration notes.
- `src/http/checkout/StripeWebhookHandler.kt` — verified payment events.
- `src/services/pdf/CommercialInvoicePdf.kt` — commercial invoice PDF generator.

## Payload preparation API

See [ORDER_EMAIL_PAYLOADS.md](ORDER_EMAIL_PAYLOADS.md) and [ORDER_EMAIL_PAYLOADS.http](ORDER_EMAIL_PAYLOADS.http) for the ShoppingCart-only preparation endpoint and nine template-specific calls. This endpoint does not activate email sending.
