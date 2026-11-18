# Tomorrow checklist — finish the four-email checkout journey

This checklist is for completing and verifying the paid-order path. Test supplier emails must go to `miguelangelorios5f@gmail.com`; do not send test messages to the real supplier.

## Already implemented

- [x] System generates a stable PO number for each paid order and includes it in the paid-order journey payload.
- [x] Supplier PO, packing-list, and delivery-note email payloads can address the supplier recipient; the real-email test script defaults to Miguel's test inbox and requires `--send-real-emails` before sending.
- [x] Dispatch estimate uses the product lead-time range (such as `2–4 Weeks`).
- [x] Verified paid Stripe webhook triggers a FedEx shipment booking when `FEDEX_MODE=SANDBOX`.
- [x] Save the FedEx AWB, label/document data, and per-origin booking status; show it in `/admin/orders`.
- [x] Prevent duplicate label creation when Stripe retries a webhook. Uncertain bookings are marked `NEEDS_REVIEW` and are not retried automatically.
- [x] Refuse to submit a physical shipment when catalogue gross weight is below calculated net tile weight.

## Tomorrow: do these in order

### 1. Correct or confirm the product shipping data

- [ ] Check product 9091: 40 tiles × 0.214 kg = **8.56 kg net** for 0.5 m², but the catalogue currently says **8.2 kg gross**. Correct the catalogue gross weight to the verified packed total, or confirm the product/weight fields are wrong.
- [ ] Confirm the pack dimensions and actual package count for the 0.5 m² order.
- [ ] Confirm the saved customer phone and supplier contact phone are usable by FedEx.

### 2. Configure FedEx sandbox

- [ ] Set `FEDEX_MODE=SANDBOX` in the test ShoppingCart runtime.
- [ ] Configure the PT sandbox credentials there: `FEDEX_TEST_PT_ACCOUNT_NUMBER`, `FEDEX_TEST_PT_CLIENT_ID`, and `FEDEX_TEST_PT_CLIENT_SECRET`.
- [ ] If testing an order with a UK origin or destination, configure the corresponding `FEDEX_TEST_UK_*` values too.
- [ ] Keep production credentials and `FEDEX_MODE=PRODUCTION` out of this test run.

### 3. Deploy and verify one paid test checkout

- [ ] Deploy the ShoppingCart changes and confirm the database migration creates `shipment_bookings`.
- [ ] Complete one Stripe test checkout with product 9091 and verify Stripe's paid webhook reaches ShoppingCart.
- [ ] Check `/admin/orders` for a `BOOKED` shipment and the returned master AWB/tracking number plus label data.
- [ ] If status is `NEEDS_REVIEW`, read the recorded reason and resolve it before another attempt. Do not clear the idempotency record or replay a possibly successful FedEx request blindly.

### 4. Finish and send the supplier documents

- [x] Calculate purchase-order unit and line prices from the catalogue supplier price per m²; show supplier subtotal, packaging, buffered shipping fees and checkout VAT rate separately in the totals.
- [ ] Connect ShoppingCart to generate and submit the PO PDF with the paid-order email journey. For this test, route the supplier message to `miguelangelorios5f@gmail.com`; production uses the supplier email in product data.
- [ ] Add/confirm the packing step that records the actual packed count, dimensions and gross weight. Generate and send the packing-list PDF/email after that confirmation.
- [ ] Generate the delivery-note PDF/email from the booked carrier, AWB, service, shipment date and confirmed delivery terms; send it to the test inbox before collection.
- [ ] Verify the customer confirmation goes to the customer's address with the invoice, and each supplier email goes to the test inbox with its matching PDF.
- [ ] Check EMAIL_SENDER history for all four expected messages, accepted status, recipient, and message ID. Confirm a repeated paid webhook creates no duplicate FedEx label or duplicate email.
## Completion criteria

- [ ] One paid Stripe test order has a FedEx sandbox AWB visible in `/admin/orders`.
- [ ] The test inbox receives the customer confirmation and the three supplier document emails, each with the correct PDF.
- [ ] Webhook replay produces no duplicate shipment or email.
- [ ] Record any remaining production-only setup separately; this test checklist must not enable production shipping.

## Test email runner

The real-email journey script defaults to `miguelangelorios5f@gmail.com`. It will not send unless invoked with `--send-real-emails`. A supplied recipient overrides the default for that run.
