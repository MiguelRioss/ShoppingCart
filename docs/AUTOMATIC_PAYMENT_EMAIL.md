# Automatic order confirmation

After a verified Stripe paid event, ShoppingCart requests the agreed order-confirmation email with the existing commercial-invoice PDF. EMAIL_SENDER owns SMTP delivery, history and duplicate suppression in Neon.

Actual invoice and fulfilment data must exist in the saved snapshot as emailDocumentData, with optional emailContext. Checkout does not currently populate this document object. Until valid data is available, automatic confirmation returns the existing pending/review response (503); payment remains saved. The protected manual send-email action remains available with actual documentData.

No substitute payment-summary email or PDF is generated. The order-confirmation template requires its invoice number and actual despatch data. Product lead-time estimates are saved from the catalogue but are not converted into invented calendar dates.

Only the agreed nine email templates and four PDF generators are used. Supplier and later delivery events still require their own workflow actions. Configure the shared EMAIL_SENDER_API_KEY and EMAIL_SENDER_BASE_URL in ShoppingCart, and EMAIL_DATABASE_URL/SMTP credentials in EMAIL_SENDER.
