# Stripe Checkout Todo

## Goal

Add a checkout flow that can take the saved cart and create a Stripe checkout experience.

## Tomorrow's Tasks

1. Review the current cart flow
   - Confirm `POST /cart` saves or updates the cart for the same `sessionId`.
   - Confirm `GET /cart` returns the saved cart.
   - Confirm `POST /cart/clear` clears the cart for the same `sessionId`.
   - Confirm cart products use real product IDs and valid quantities.

2. Design checkout routes
   - [x] Use `POST /checkout` to create a checkout session from a saved cart.
   - [x] Keep the same route architecture already used by the project.

3. Plan the Stripe integration
   - [x] Use Stripe Checkout Sessions.
   - [x] Create checkout from `sessionId` for the current cart flow.
   - [x] Return checkout session id, provider, status, and URL when available.
   - [ ] Link `StripePaymentProvider` to the real Stripe API endpoint/SDK.

4. Create a checkout service layer
   - [x] Keep business decisions inside services.
   - [x] Validate that the cart exists.
   - [x] Validate that the cart is not empty.
   - [x] Build checkout line items from saved cart totals.

5. Create a Stripe adapter
   - [x] Keep Stripe API calls outside HTTP handlers.
   - [x] Use `PaymentProvider.createCheckout(...)`.
   - [x] Add `StripePaymentProvider.createCheckout(...)`.
   - [ ] Replace current `api_link_missing` placeholder with the actual Stripe API call.

6. Add checkout service errors
   - [x] Add new entries to `ServiceErrorCode`.
   - [x] Add cart not found, cart is empty, and checkout creation failed.
   - [x] Let the HTTP error module translate service errors into HTTP status responses.

7. Update the frontend tester
   - Add a checkout button after save cart.
   - Show the request immediately.
   - Show loading while waiting for the response.
   - Display the checkout URL or Stripe session ID.

8. Test the complete flow
   - Register
   - Login
   - Add product to draft cart
   - Save cart
   - Get cart
   - Create checkout
   - Verify the Stripe response

## Possible Commit Message

```text
Add Stripe checkout route and payment session flow
```
