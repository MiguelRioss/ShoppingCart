# Client Journey — Manual Test Checklist

Use a test customer, Stripe test payments, and products with valid catalogue/shipping data. Record the customer email, both cart session IDs, order IDs and Stripe checkout session IDs below. The real-email journey test defaults all four messages to `miguelangelorios5f@gmail.com`; it requires the explicit `--send-real-emails` flag.

This checklist distinguishes implemented ShoppingCart behaviour from the email preparation/rendering steps. Email sending and scheduling in EMAIL_SENDER_v2 are not implemented yet. Rendering an email does not send it.

## Test record

- Date: ____________________
- Frontend URL: ____________________
- ShoppingCart API URL: ____________________
- EMAIL_SENDER API URL: ____________________
- Customer email: ____________________
- Customer user ID: ____________________
- Cart A session ID: ____________________
- Cart B session ID: ____________________
- Order A ID / checkout session ID: ____________________
- Order B ID / checkout session ID: ____________________

## 1. Check the running services

- [ ] Open the checkout frontend through its HTTP server, rather than as a file.
- [ ] Confirm the frontend calls the intended ShoppingCart API environment.
- [ ] Confirm the latest ShoppingCart changes are deployed if testing Render. Local tests do not prove the deployed service has these changes.
- [ ] Call `GET {EMAIL_SENDER_API}/health`; the existing service should remain available.
- [ ] Call `GET {EMAIL_SENDER_API}/v2/templates`; expect nine template definitions after the new deployment.
- [ ] Confirm Stripe uses test payments and webhook events target this ShoppingCart environment.
- [ ] Confirm the account's delivery address and product shipping data are complete.

**Expected:** Existing services work; v2 template routes are available without replacing V1. A shipping configuration or rate error must be resolved before testing a payable checkout; do not silently substitute an offline rate for a live-rate test.

## 2. Register or sign in as the customer

- [ ] Register a test account or sign in to an existing test account.
- [ ] Save first/last name, email, phone, delivery address and invoice details where required.
- [ ] Verify the authenticated account information is correct.

**Expected:** Login returns a valid bearer token. Customer details used by checkout come from the authenticated account. Never include tokens/passwords in the test notes or screenshots.

## 3. Save TWO carts for the SAME user

- [ ] Save Cart A with its own `sessionId`, one full product and a known quantity.
- [ ] Save Cart B with a different `sessionId`, a different quantity and/or a sample.
- [ ] Fetch each using `GET /cart?sessionId=...`.
- [ ] Verify both have the same user ID but different cart IDs.
- [ ] Change Cart B and save it again with Cart B's session ID.
- [ ] Fetch Cart A again and verify its products and quantities are unchanged.
- [ ] Verify saving Cart B again updates the same Cart B ID.

**Expected:** Both carts remain independent. Always supply the selected session ID. Fetching `/cart` without a session returns the most recently saved user cart for compatibility; it is not a list of every saved cart.

## 4. Start checkout but DO NOT pay

- [ ] Start checkout for Cart A.
- [ ] Verify the product names, sample/full-product selection, quantities, shipping and total in Stripe.
- [ ] Record the pending order ID and Stripe checkout session ID.
- [ ] Leave checkout or cancel without paying.
- [ ] Confirm the order is not marked PAID and Cart A remains saved.
- [ ] Confirm Cart B remains saved too.
- [ ] Verify the cancel/error page returns to the same frontend server and folder.

**Expected:** A saved order exists with its checkout-time snapshot. Leaving checkout must not clear the cart or count as payment.

### Email checkpoint: incomplete checkout

- [ ] Using an administrator token, prepare `incomplete-checkout` for the pending/expired order.
- [ ] Render the returned payload through EMAIL_SENDER's `POST /v2/render`.
- [ ] Check the actual customer name, exact saved backend product titles, images, quantities and units.
- [ ] Confirm there is no invented sample title/reference or preview customer data.
- [ ] Confirm no email was sent automatically.

**Pending implementation:** Select the 12–24 hour reminder delay. The future scheduler must recheck payment and suppress the reminder once paid.

## 5. Complete a Stripe TEST payment

- [ ] Resume or create a checkout for the intended Cart A session and complete a Stripe test payment.
- [ ] Check that the success URL uses the same frontend host, port and folder and the page loads.
- [ ] Check Stripe's event log: a paid checkout webhook should reach ShoppingCart and receive a successful response.
- [ ] Verify the order status becomes PAID and the paid amount/currency are recorded.
- [ ] Fetch Cart A by its exact session ID; expect no saved cart (404).
- [ ] Fetch Cart B; expect it still to exist unchanged.
- [ ] Open the dashboard Orders view and verify the paid order remains.
- [ ] Open View products and verify the purchased product details and image.

**Expected:** Verified payment clears only the purchased, unchanged cart. The success redirect alone does not mark the order paid or clear the cart. With asynchronous payments, an unpaid completed event leaves the cart until payment succeeds.

### Email checkpoint: order confirmation + invoice

- [ ] Prepare `order-confirmation` using the paid order ID, actual expected despatch date and invoice reference.
- [ ] Provide the existing invoice PDF reference for this order.
- [ ] If invoice/date data is missing, verify the API returns 422 with the missing fields; do not replace them with invented values.
- [ ] Render the payload and verify customer, order reference, products, quantities, prices and date.
- [ ] Confirm the required invoice attachment is listed.

**Pending implementation:** Order-specific invoice generation/storage and attachment delivery. The renderer returns inline-logo data and required PDF types; it does not fetch or attach the order PDF or send the message.

## 6. Try duplicate and delayed payment events

- [ ] Resend the same test paid event from Stripe's event tools.
- [ ] Verify the same order remains PAID without creating another order.
- [ ] Verify Cart B remains unchanged.
- [ ] Create a new cart using Cart A's old session ID and resend the old paid event.
- [ ] Verify the new cart is preserved.
- [ ] In a separate test, edit a cart after creating checkout but before payment confirmation; verify a later paid event preserves the edited cart.

**Expected:** Cleanup is repeat-safe and never erases new purchases or post-checkout edits. The original order snapshot remains available even after cart cleanup.

## 7. Supplier purchase order

- [ ] Prepare `supplier-purchase-order` for a paid order with a real supplier ID.
- [x] Use the system-generated PO number for the paid order.
- [ ] Use the product's lead-time range (for example, `2–4 Weeks`) as the dispatch estimate, until an exact date is confirmed.
- [ ] Verify the production recipient/contact and product references match the catalogue supplier; the test journey redirects supplier messages to the test inbox above.
- [ ] Verify only that supplier's order products appear.
- [x] Verify the PO uses supplier price per m² and separately shows supplier subtotal, packaging, shipping fees and the checkout VAT rate.
- [ ] Send test messages only to the test inbox above.

**Expected:** Complete supplier payload or an explicit missing-data error. A document belonging to another order/supplier is rejected by the request validation.

## 8. Revised despatch date

- [ ] Prepare `revised-despatch-date` with the previous date and a later revised date, optionally including a reason.
- [ ] Render and verify both dates and the customer/order.
- [ ] Try the same or an earlier date; expect validation failure.

**Pending implementation:** Persist original and last-communicated dates, and send only when the new date is later. An ordinary order edit must not trigger another message.

## 9. Supplier packing list and delivery note

- [ ] Prepare `supplier-packing-list` with the actual packing-list number, units, packages, weight and dimensions.
- [ ] Provide the existing PDF for the correct order and supplier.
- [ ] Verify the saved product cards and derived boxes/area.
- [ ] Prepare `supplier-delivery-note` with the actual document number, courier, tracking/AWB and incoterm.
- [ ] Provide the matching existing delivery-note PDF.
- [ ] Render both and check the recipient and order references.

**Pending implementation:** Automatic document generation/storage wiring and fulfilment events. The supplier should receive these before collection; rendering alone does not notify them.

## 10. Goods despatched

- [ ] Confirm actual collection/despatch in the test workflow.
- [ ] Prepare `goods-despatched` with courier, tracking number, tracking URL and estimated delivery date.
- [ ] Render and verify the customer, order and working courier link.

**Pending implementation:** Automatic courier/fulfilment integration. A shipping label or tracking number alone must not falsely indicate collection.

## 11. Delivery confirmation

- [ ] Obtain or simulate a verified delivered event in the test workflow.
- [ ] Prepare `delivery-confirmation` with the actual delivery date.
- [ ] Render and verify the customer, order, inspection instructions and 24-hour contact request.

**Pending implementation:** Delivered-event storage and automatic preparation/sending from courier events. The preparation endpoint itself does not verify a FedEx delivery event.

## 12. Seven-day thank-you and discount

- [ ] Prepare `post-delivery-thank-you` with a real one-use discount code, terms where applicable and the separate marketing-consent URL.
- [ ] Render and verify all values.
- [ ] Verify the discount is not conditional on marketing consent.

**Pending implementation:** Real discount issuance/redemption checks, confirmed-delivery timing, seven-day scheduling and positive consent recording. Rendering does not create a discount or record consent.

## 13. Check preparation/rendering API errors

- [ ] No administrator token: ShoppingCart preparation returns 401.
- [ ] Ordinary customer token: preparation returns 403.
- [ ] Unknown order: 404.
- [ ] Unknown template or malformed body: 400.
- [ ] Wrong payment state: 409.
- [ ] Missing required data or invalid PDF/link/date inputs: 422.
- [ ] Attempt to override customer/product data through `context.fields`: rejected.
- [ ] POST `/v2/send`: 501, not implemented.
- [ ] POST `/v2/schedule`: 501, not implemented.

**Expected:** Nothing is silently sent, scheduled or populated with fake fallback data.

## How to check the paid-order journey

Run `services.order.StartClientJourneyTest` locally. It creates a paid order in memory and checks the ordered nine-template request and invoice PDF. In EMAIL_SENDER, run `npm test` to check acceptance, storage, rendering and duplicate suppression. Later templates require actual fulfilment events before they can be sent.

## Results

- [ ] Two independent carts: PASS / FAIL
- [ ] Same-session save updates only its cart: PASS / FAIL
- [ ] Verified payment recorded: PASS / FAIL
- [ ] Purchased cart cleared: PASS / FAIL
- [ ] Other cart retained: PASS / FAIL
- [ ] Duplicate/delayed-event safety: PASS / FAIL
- [ ] Order/products remain available: PASS / FAIL
- [ ] Frontend return URLs load: PASS / FAIL
- [ ] Nine payloads/rendered emails reviewed: PASS / FAIL / BLOCKED
- [ ] Existing EMAIL_SENDER V1 routes still work: PASS / FAIL

Issues / missing data / screenshots: ____________________

## Related references

- [ORDER_EMAIL_TRIGGERS.md](ORDER_EMAIL_TRIGGERS.md)
- [MULTIPLE_CARTS.md](MULTIPLE_CARTS.md)
- `C:/Projects/EMAIL_SENDER/EMAIL_SENDER_v2/README.md`
