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
