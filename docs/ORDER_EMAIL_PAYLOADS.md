# Order email API payloads

For in-memory PDF generation and submission to EMAIL_SENDER, see [ORDER_EMAIL_SEND.md](ORDER_EMAIL_SEND.md).

ShoppingCart prepares all render data. EMAIL_SENDER will later select the template and render the supplied variables. It must not invent product names, sample references, quantities, prices, dates, recipients or document IDs.

## Preparation endpoint

`POST /admin/orders/:orderId/email-payload`

Requires an administrator login bearer token, checked against `ADMIN_USER_IDS`. Uses the internal order UUID from `GET /admin/orders`, not the Stripe checkout session ID. The endpoint reads data only: it does not send email, invoke EMAIL_SENDER, schedule jobs, issue discounts, generate invoices, or change orders.

The nine example calls are in [ORDER_EMAIL_PAYLOADS.http](ORDER_EMAIL_PAYLOADS.http). Fill its variables with actual workflow values before running a request. ISO dates use `YYYY-MM-DD`; they are returned in readable English date format for rendering.

```json
{
  "templateId": "order-confirmation",
  "context": {
    "fields": {
      "expected_despatch_date": "<actual ISO date>",
      "invoice_number": "<existing invoice number>"
    },
    "documents": {
      "invoice": {
        "orderId": "<this order UUID>",
        "documentId": "<existing stored invoice ID>",
        "filename": "<existing invoice PDF filename>",
        "url": "<HTTPS URL for that existing PDF>"
      }
    }
  }
}
```

Angle-bracket values above describe data sources; they are not values to send literally. Protected/expiring storage URLs can be resolved by the future sender integration. No URL is fetched by this preparation endpoint.

## Nine template IDs and additional data

Customer recipient and first name are resolved from the order snapshot, falling back to its user record for older snapshots. Product lists are built automatically for templates marked below; do not include them in context.fields.

| templateId | Additional context.fields | Products | Required document |
| --- | --- | --- | --- |
| order-confirmation | expected_despatch_date, invoice_number | All saved product lines | invoice |
| supplier-purchase-order | po_number, required_despatch_date | Only selected supplier's lines | purchase-order |
| incomplete-checkout | None | All saved product lines | None |
| supplier-packing-list | packing_list_number, quantity_units, package_count, gross_weight, package_dimensions | Only selected supplier's lines | packing-list |
| supplier-delivery-note | delivery_note_number, courier, tracking_number, incoterm | No card block in this template | delivery-note |
| revised-despatch-date | previous_despatch_date, revised_despatch_date; optional despatchUpdateNote | No card block | None |
| goods-despatched | courier, tracking_number, estimated_delivery_date, tracking_url | No card block | None |
| delivery-confirmation | delivery_date | No card block | None |
| post-delivery-thank-you | discount_code, marketing_consent_link; optional discount_terms | No card block | None |

Supplier calls also require context.supplierId, matching a real catalogue supplier on the saved order products. Recipient email and contact name come from that supplier record. Supplier documents must include a matching supplierId and orderId; customer documents must match orderId. Document references are supplied by the trusted admin/workflow caller because persistent order-document storage is not yet implemented. This validates reference structure and matching IDs, not ownership in an external storage system.

`context.shipmentId` can be included to distinguish shipment-specific payloads. Shipment tracking and delivery dates currently come from explicit workflow context; this endpoint does not independently query or verify courier events. The dates, packing details and discount code must be obtained from actual records by the caller, not placeholders. Corresponding storage adapters can replace these inputs once the fulfilment/promotion workflows are connected.

## Output contract for the later EMAIL_SENDER integration

```text
{
  schemaVersion: 1,
  brand: "deferranti",
  templateId: one of the nine IDs,
  orderId: saved order ID,
  checkoutSessionId: saved Stripe checkout ID,
  to: { email, name },
  variables: { order_reference, ...all variables used by that template },
  attachments: [{ documentId, filename, url, contentType: "application/pdf", type }],
  idempotencyKey: stable preparation key
}
```

For product-card templates, `variables.products` contains:

```text
{
  productId,
  name,                 // exact saved checkout/backend product title
  quantity,             // saved square metres or sample units
  unit,                 // m², pieces, or units when the original unit is unavailable
  imageUrl,             // saved image, falling back to the real catalogue image
  lineTotal, currency,  // customer emails only; exact saved retail line total
  supplierProductId     // real saved/catalogue reference when available
}
```

No synthetic sample titles or sample SKUs are created. Product ID 0 shipping lines are excluded. Quantities use the saved cart-product metadata, rather than mistaking a box count for square metres. Order total prices and titles are never replaced by current catalogue prices/titles. Older missing images and supplier contact/reference data may be resolved from the current catalogue, and that dependency can fail validation. Supplier prices belong to the PO/document workflow: customer retail totals are deliberately omitted from supplier cards.

`boxes` and packing-list `quantity_m2` are calculated from the selected supplier's saved cart-product quantities. `quantity_units`, actual packed weight/dimensions and package count are explicit fulfilment inputs; they are not inferred from retail quantity.

The payload's variables are plain data. The future sender uses templateId to select HTML and derive the subject, escapes variables, renders product cards and adds its logo/footer. It then attaches the referenced existing PDFs. There is no HTML or hardcoded preview customer data in this API response. The current De Ferranti HTML subject/placeholder contract is preserved; EMAIL_SENDER code has not been changed as part of this work.

## Validation and lifecycle boundaries

- 200: complete payload prepared; no email sent.
- 400: malformed body, unknown templateId, or unsupported top-level body fields.
- 401/403: missing login or not an administrator.
- 404: order not found.
- 409: confirmation/supplier email requested before payment, or incomplete-checkout requested for a paid/failed order.
- 422: required rendering data is missing/invalid; response lists exact fields. Client-supplied customer/product overrides are rejected. Unsafe URLs, incorrect document order/supplier references and non-later revised dates are rejected.
- 503: database/preparation failure; internal details are not exposed.

No missing-data values are fabricated. New checkout snapshots preserve customerFirstName; older orders can use their actual user record. An incomplete checkout request refers to a persisted pending/expired checkout order; it is not an email for an arbitrary unsaved browser cart.

Preparation is not permission to send. The 12–24 hour reminder delay, seven-day follow-up, confirmed courier events, real discount validity, marketing consent rules, durable jobs, document ownership checks and atomic send deduplication must be enforced by the eventual workflow integration. The key returned here is an input to that integration, not a stored send log. See [ORDER_EMAIL_TRIGGERS.md](ORDER_EMAIL_TRIGGERS.md).
