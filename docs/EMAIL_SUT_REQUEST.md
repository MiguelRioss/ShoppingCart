# Approved order-confirmation SUT

The SUT uses the existing order-confirmation email and CommercialInvoicePdf generator. It requires actual document data; it never creates a replacement invoice or guesses missing values.

```powershell
.\gradlew.bat prepareEmailSut -PorderId=YOUR_ORDER_UUID -PdocumentData=C:\path\to\actual-invoice-data.json --console=plain
```

Use the documentData format documented in ORDER_EMAIL_SEND.md. An optional `-Pcontext=path.json` supplies genuine workflow context. Data may also be present in the saved snapshot as emailDocumentData/emailContext. Missing required data fails preparation without sending or updating the order.

Output is `.cache/email-sut/order-confirmation.json`, containing the approved commercial-invoice PDF bytes. The existing purchase-order, packing-list and delivery-note generators retain their respective supplier email mappings.
