# Automatic order confirmation

After a verified Stripe paid event, ShoppingCart validates the saved order and payment, generates its invoice PDF, and sends one JSON request with `order` and `documents.invoice` to `POST /v2/start-client-journey`. ShoppingCart does not select email templates. EMAIL_SENDER is responsible for mapping these facts to emails.

The EMAIL_SENDER endpoint now accepts this request and maps it to the order-confirmation email. The manual ShoppingCart email preparation and sending endpoints have been removed. Configure `EMAIL_SENDER_API_KEY` and `EMAIL_SENDER_BASE_URL` in ShoppingCart, and `EMAIL_DATABASE_URL`, `DEFERRANTI_EMAIL_FROM` and SMTP credentials in EMAIL_SENDER.
