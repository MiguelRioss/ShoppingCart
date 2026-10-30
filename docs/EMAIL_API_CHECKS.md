# ShoppingCart checks EMAIL_SENDER's API

Run from ShoppingCart. These calls target the deployed service on Render; no EMAIL_SENDER checkout is needed.

```powershell
.\gradlew.bat -q emailApiCheck
```

The quiet command prints a short PASS/FAIL for template listing/details, rendering, authentication, history and the expected unimplemented scheduler response. It sends no email. It reads ShoppingCart's `.env` for `EMAIL_SENDER_API_KEY` and optional `EMAIL_SENDER_BASE_URL`; the default server is `https://email-sender-0fks.onrender.com`.

## Check one route

```powershell
.\gradlew.bat -q emailApiCheck -Proute=templates
.\gradlew.bat -q emailApiCheck -Proute=render
.\gradlew.bat -q emailApiCheck -Proute=auth
.\gradlew.bat -q emailApiCheck -Proute=history
.\gradlew.bat -q emailApiCheck -Proute=schedule
```

## Send a prepared real-data request

After preparing the approved-template request described in EMAIL_SUT_REQUEST.md:

```powershell
.\gradlew.bat -q emailApiCheck -Proute=send -PrequestFile=.cache/email-sut/order-confirmation.json
```

This sends one real email through Render to the existing Gmail override. The request must supply its actual variables and approved PDF attachment; the checker does not invent an invoice or email. It repeats the same key, then verifies the saved message ID and one matching accepted Neon history record.

Optional properties: `-PbaseUrl=https://your-service.onrender.com` and `-Preference=ORDER_REFERENCE` (for history). Requires Node 22+ on PATH; no npm dependencies are needed in ShoppingCart. The Gradle task invokes `scripts/check-email-api.mjs`. It is a live API checker, separate from Kotlin/JUnit unit tests and from the removed full customer-journey test.
