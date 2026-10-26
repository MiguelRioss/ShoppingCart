# Multi-origin shipping

## Business model

One customer checkout may contain products supplied from different locations.
The customer still sees and pays one order, but the carrier treats each origin
as a separate shipment:

```text
Customer order
  Product A                                  EUR 90
  Product B                                  EUR 75
  Shipping from supplier A                   EUR 25
  Shipping from supplier B                   EUR 20
  Total                                     EUR 210
```

The checkout currently presents the two shipping costs as one consolidated
line (`Shipping (2 origins)`, EUR 45). This keeps one Stripe payment while
preserving the carrier rule that packages with different origins cannot share
one shipment.

## Implemented checkout flow

1. The saved cart is loaded.
2. Each cart line is enriched with supplier, customs, weight, and dimensions
   from the product catalogue.
3. Lines are grouped by their complete supplier shipping address.
4. Products from the same address become packages in one `ShipmentRateRequest`.
5. Products from different addresses produce separate rate requests.
6. For every origin, only EUR quotes are considered and the cheapest is chosen.
7. The chosen amounts are added together.
8. Stripe receives all product lines plus one consolidated shipping line.

For one origin, the selected FedEx service name and code are preserved. For
multiple origins, the consolidated charge uses `MULTI_ORIGIN`, because each
origin can select a different FedEx service.

## Examples

Two products from the same supplier:

```text
1 checkout
1 FedEx rate request
2 packages
1 selected shipping charge
```

Two products from different suppliers:

```text
1 checkout
2 FedEx rate requests
1 package in each request
2 selected shipping charges
1 consolidated shipping amount paid by the customer
```

## Shipment creation after payment

Rate calculation does not create a shipment or label. Shipment creation should
happen only after Stripe confirms payment through a verified webhook.

At that point the application must recreate or load the origin groups and call
`createShipment` once per origin. Each result has its own master tracking
number, package tracking numbers, labels, documents, and alerts.

```text
Stripe payment succeeded
  -> shipment for supplier A -> tracking and label A
  -> shipment for supplier B -> tracking and label B
```

This post-payment orchestration is not yet implemented in ShoppingCart. The
middleware supports `createShipment`, but ShoppingCart currently integrates it
only for rate calculation during checkout.

## Production decisions to evaluate

- **Supplier identity:** grouping currently uses the complete shipping address.
  A stable supplier or warehouse ID should be preferred if the catalogue has one.
- **Selected services:** persist the selected quote for every origin so that the
  service and amount used after payment can be audited.
- **Quote expiry:** decide whether to accept the checkout quote or recalculate it
  before creating shipments.
- **Partial failure:** if one shipment succeeds and another fails, store both
  outcomes and retry only the failed origin. Do not recreate a successful label.
- **Idempotency:** Stripe may deliver a webhook more than once. Shipment creation
  must use a stored idempotency key per order and origin.
- **Cancellations and refunds:** define whether cancelling one supplier shipment
  creates a partial refund or cancels the complete order.
- **Delivery estimates:** the consolidated checkout charge currently exposes the
  latest selected delivery date, representing when the complete order is expected.
- **Currency:** checkout accepts only EUR carrier quotes, ensuring all Stripe line
  items use the same currency.
- **Customer display:** decide whether the frontend should show one consolidated
  shipping line or an origin-by-origin breakdown before payment.

## Automated coverage

`ShipmentShippingChargeProviderTest` verifies that:

- products sharing an origin are sent as packages in one rate request;
- products with two origins generate two rate requests;
- the cheapest EUR quote is selected independently for each origin;
- the two selected amounts are summed into one checkout shipping charge;
- a single-origin checkout retains the original FedEx service details.

## FedEx billing account routing

The default billing account is PT. If either the collection/origin or delivery/destination country is GB (UK alias accepted), the UK billing account is used. This decision is made independently for each supplier-origin quote. It changes the billing account, not the collection or delivery address. A missing UK account fails that quote and never falls back to PT. The adapter also routes shipment preparation, but checkout currently requests rates only; it does not book shipments.

FEDEX_MODE controls carrier access: OFFLINE, SANDBOX, or PRODUCTION. SHIPPING_MODE is not read. APP_MODE independently controls database access.

For production configure these server-only values:

- FEDEX_PRODUCTION_PT_ACCOUNT_NUMBER
- FEDEX_PRODUCTION_PT_CLIENT_ID
- FEDEX_PRODUCTION_PT_CLIENT_SECRET
- FEDEX_PRODUCTION_UK_ACCOUNT_NUMBER
- FEDEX_PRODUCTION_UK_CLIENT_ID
- FEDEX_PRODUCTION_UK_CLIENT_SECRET

Account country is PT or GB based on the selected route. Existing unqualified FEDEX_PRODUCTION_ACCOUNT_NUMBER / CLIENT_ID / CLIENT_SECRET values are supported only for PT. Sandbox uses the same names with FEDEX_TEST_ instead of FEDEX_PRODUCTION_. Production credentials must not be used in sandbox.

Set FEDEX_MODE=PRODUCTION locally and on the backend host, then restart/redeploy to apply. Local changes do not deploy the Render service.

### Latest live check (26 October 2026)

The originally configured account ended in 7010; the owner confirmed that this is not the PT account. The explicit FEDEX_PRODUCTION_PT_ACCOUNT_NUMBER now uses the supplied PT account ending in 9738. With the existing client credentials, the correct PT number is rejected by FedEx with ACCOUNT.NUMBER.MISMATCH, including a direct rate request with matching shipper and sender-payor account numbers. Check that the PT account is linked/enabled for the production API project or supply its corresponding FEDEX_PRODUCTION_PT_CLIENT_ID and FEDEX_PRODUCTION_PT_CLIENT_SECRET. This response alone does not establish which portal setting is missing.

Earlier GBP quotes came from the wrong account. They do not establish the correct PT account's currency. Direct tests on that old account also found that PREFERRED alone returned EUR, whereas ACCOUNT/LIST/PREFERRED together returned only GBP. Any middleware currency-request change should be verified using the correct PT account after account access is fixed. No automatic currency conversion was added.

UK account credentials are still not configured. Routing/configuration and existing shipping/checkout checks passed (20 tests). The opt-in FedExPtRateSmokeTest (RUN_FEDEX_PT_RATE_SMOKE=true) currently documents the account-mismatch blocker against the correct PT number. Checks request rates only and do not create shipments or labels. These local settings and code changes have not been deployed to Render.