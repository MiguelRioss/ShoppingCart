# Multiple saved carts and paid-order cleanup

A user may save multiple carts with different sessionId values. POST /cart updates only the cart for its supplied sessionId. Reusing that session updates that same cart ID. Logging in with a guest session attaches that cart without replacing the user's other carts.

GET /cart?sessionId=... returns only the requested session; a missing/paid cart returns 404 rather than silently returning another cart. GET /cart with authentication and no sessionId retains compatibility by returning the most recently saved user cart. Clients managing multiple carts should always send their selected sessionId when saving, fetching and checking out.

The migration removes shopping_carts_user_id_key and keeps the user foreign key. Existing carts and orders remain. Saves are serialized per session to avoid creating duplicate carts during concurrent saves. No one-cart-per-user rule remains.

A verified Stripe paid webhook marks the order PAID and clears only the purchased cart. Pending, failed, expired and unsigned events do not clear carts. The checkout snapshot records cart ID and saved timestamp. Cleanup deletes only that saved version; a cart edited after checkout or newly created in the same browser remains. Legacy snapshots without timestamps are cleaned only if their saved product contents still match. Deleting a cart cascades to its cart-product rows, while the order snapshot remains.

Cleanup runs after payment persistence. If database cleanup fails, the webhook returns an error so Stripe can retry; repeated payment events and already-cleared carts are safe. This requires STRIPE_WEBHOOK_SECRET and Stripe event delivery to POST /webhooks/stripe. Merely opening or leaving checkout, or returning to the success URL, does not count as verified payment.

Verification uses isolated temporary PostgreSQL user/cart/order rows, removed afterwards, and signed synthetic webhook events; no real Stripe charge is made. Tests cover independent carts, updating a single session, guest attachment, clearing only the purchased cart, duplicate webhooks, delayed payments after edits, unpaid/failed/expired events, and preservation of the order.
