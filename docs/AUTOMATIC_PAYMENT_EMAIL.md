# Automatic paid-checkout confirmation

ShoppingCart calls the email service after a valid Stripe webhook persists a `PAID` order. Both `checkout.session.completed` with `payment_status: paid` and `checkout.session.async_payment_succeeded` are supported. Browser redirects never trigger sending. Unpaid, failed, expired, unrelated and invalidly signed events do not send a confirmation.

The automatic template is `payment-confirmation`. It shows the saved product names, images, quantities and line totals. `general_lead_time` is read from product data during checkout and stored as `generalLeadTime` in each snapshot item. The message lists each product's estimate, for example `2-4 Weeks`; it does not convert this into a promised calendar date. Missing values say `To be confirmed`. Catalogue edits after checkout do not change these estimates.

ShoppingCart attaches an in-memory `payment-summary.pdf`, using the saved product/shipping lines and the amount/currency confirmed by Stripe. This is explicitly a payment summary, not a VAT or customs invoice. The existing `order-confirmation` commercial-invoice action remains available when the real customs and fulfilment details are supplied; the webhook does not invent them.

EMAIL_SENDER must deploy its matching `payment-confirmation` template. Its V2 SMTP adapter currently redirects every recipient to `miguelangelorios5f@gmail.com` and sends from `info@tabularasaretreat.com`, with automatic copies disabled.

Required Render configuration:

- ShoppingCart: `STRIPE_WEBHOOK_SECRET`, `EMAIL_SENDER_BASE_URL`, `EMAIL_SENDER_API_KEY`, normal database configuration.
- EMAIL_SENDER: matching `EMAIL_SENDER_API_KEY` and working TRR SMTP credentials.
- Stripe webhook endpoint: ShoppingCart `/webhooks/stripe`, subscribed to the checkout events above. Test/live signing secrets must match the endpoint's mode.

## Duplicate and error handling

EMAIL_SENDER owns delivery history and duplicate suppression in Neon, in its email_deliveries table. ShoppingCart forwards the same stable key when Stripe repeats an event. A successful replay returns the saved acceptance result; in-progress or uncertain records are not sent again.

Email failure never rolls back a paid order. The webhook returns 503 so Stripe can retry the same event. EMAIL_SENDER checks the persistent record on each retry. SMTP acceptance is not proof of inbox delivery. Failed/uncertain outcomes require review in EMAIL_SENDER.

The old ShoppingCart payment_email_submissions table is no longer used. Existing records are retained for reconciliation, not dropped.

## Checkout test

1. Deploy both services and confirm their shared key and SMTP settings.
2. Start a new checkout with product lead time populated and complete payment in Stripe's test mode.
3. Check that the order is paid and EMAIL_SENDER’s `email_deliveries.status` becomes `accepted`.
4. Open the Gmail confirmation; verify products, estimates, logo/footer and payment-summary attachment.
5. Resend the same paid event from Stripe. Confirm no second SMTP submission and the stored state remains `accepted`.
6. For a delayed payment, the initial unpaid completion must send nothing; the later paid event sends one confirmation.

This does not trigger supplier, shipping, delivery or follow-up emails. Those require their actual fulfilment events.
