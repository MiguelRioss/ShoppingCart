# Send an order email with generated PDF bytes

ShoppingCart owns PDF generation. The protected action below builds the template variables and recipient from the saved order, generates the selected PDF in memory, and posts JSON to EMAIL_SENDER `/v2/send`. It does not save the PDF.

## Configuration

- `EMAIL_SENDER_BASE_URL`: EMAIL_SENDER host, for example `https://your-service.onrender.com`. Do not append `/v2/send`. A base ending in `/email-sender` also works.
- `EMAIL_SENDER_API_KEY`: optional bearer credential, if required by the receiving service.
- `DEFERRANTI_LOGO_PATH`: optional server-controlled logo path; defaults to the bundled logo.

EMAIL_SENDER currently returns `501` for `/v2/send`. This connection is ready, but delivery requires implementing that route, accepting the attachment contract below, and configuring SMTP there.

## ShoppingCart action

`POST /admin/orders/{orderId}/send-email`, authenticated with an administrator bearer token:

```json
{
  "templateId": "order-confirmation",
  "context": { "fields": {} },
  "documentData": {
    "references": {
      "customerOrderNumber": "THE-EXACT-ORDER-ID",
      "invoiceNumber": "INV-2026-1042",
      "requiredDespatchDate": "2026-11-12"
    },
    "parties": {
      "purchaser": { "name": "Actual purchaser", "addressLines": ["Actual address"] },
      "supplier": { "name": "Actual supplier", "addressLines": ["Actual address"] },
      "shipFrom": { "name": "Actual sender", "addressLines": ["Actual address"] },
      "deliverTo": { "name": "Actual recipient", "addressLines": ["Actual address"] },
      "importerOfRecord": { "name": "Actual importer", "addressLines": ["Actual address"] },
      "seller": { "name": "Actual seller", "addressLines": ["Actual address"] }
    },
    "goods": [{
      "productName": "Exact saved order product name",
      "area": "12.5 m2", "boxes": "25", "tiles": "1250",
      "unitPrice": "EUR 90.00", "lineTotal": "EUR 1125.00"
    }],
    "totals": {
      "supplierSubtotal": "Actual subtotal",
      "vatTreatment": "Actual VAT treatment",
      "purchaseOrderTotal": "Actual total"
    },
    "packing": {},
    "shipment": {}
  }
}
```

The example describes the shape; replace every example value with genuine order and fulfilment data. Financial, party and packing details are supplied by the trusted caller, not inferred or automatically reconciled with the order. No sample PDF fixture is used in production. Product names and order reference must match the saved order.

All six parties require `name` and nonempty `addressLines`. Optional party fields are `contactName`, `phone`, `email`, `customerType`, `vatNumber`, `eoriNumber`. Goods also accept `specificationLines`, `unitPriceLabel`, `deFerrantiSku`, `supplierProductId`, `hsCode`, `originCountry`, `netWeight`, `grossWeight`, `packageDescription`, `standardisedProductId`.

| Template ID | Generator | Required document references / details |
| --- | --- | --- |
| `order-confirmation` | Commercial invoice | `references.invoiceNumber`, `references.requiredDespatchDate` |
| `supplier-purchase-order` | Purchase order | `references.purchaseOrderNumber`, `references.requiredDespatchDate`; `context.supplierId` |
| `supplier-packing-list` | Packing list | `references.packingListNumber`; `packing.packageCount`, `packing.grossWeight`, `packing.dimensions`; `context.supplierId`, `context.fields.quantity_units` |
| `supplier-delivery-note` | Delivery note | `references.deliveryNoteNumber`; `shipment.courier`, `shipment.trackingNumber`, `shipment.incoterm`; `context.supplierId` |

Reference numbers, dates and shipping/packing fields above are also copied into email variables. Conflicting context values are rejected. Dates accept ISO or `dd/MM/yyyy`. Additional reference fields include `purchaseOrderDate`, `packingDate`, `deliveryDate`. Packing accepts `packageType`, `contents`, `packingCost`, `handlingLines`; shipment accepts `service`, `awb`, `purpose`, `deliveryTerms`.

Other templates use the same endpoint without `documentData`. Their required context is documented in [ORDER_EMAIL_PAYLOADS.md](ORDER_EMAIL_PAYLOADS.md). The existing `/email-payload` action still only prepares a payload and never sends it.

## Outgoing attachment contract

The POST includes `templateId`, `to` (`email` and `name`), `variables`, `idempotencyKey` and `attachments`. Each generated attachment contains:

```json
{
  "documentId": "order-id:invoice:INV-2026-1042",
  "type": "invoice",
  "filename": "invoice.pdf",
  "contentType": "application/pdf",
  "contentBase64": "BASE64_ENCODED_PDF_BYTES"
}
```

EMAIL_SENDER must decode `contentBase64` into attachment bytes. No local path or PDF download URL is sent. PDFs are limited to 8 MiB each; the complete outgoing JSON is limited to 12 MiB. Configure the receiving JSON limit accordingly.

The stable key is also forwarded in the `Idempotency-Key` header. There are no automatic retries or local delivery records; EMAIL_SENDER must enforce deduplication. Network failures can leave delivery uncertain, so retain the same key when retrying.

## Results

- `202`: EMAIL_SENDER accepted the request; this does not prove delivery.
- `422`: missing or inconsistent email/document data.
- `404`: order not found; `401`/`403`: authentication/access failure.
- `503`: missing/invalid EMAIL_SENDER configuration.
- `502`: EMAIL_SENDER/network failure; `upstreamStatus` is included when available (currently `501` for the unimplemented send route).
- `500`: PDF generation or other unexpected failure.

This is an explicit admin action. Automatic payment, despatch and scheduler triggers are not installed by this connection.
