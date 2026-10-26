# Admin Users API

GET /admin/users requires Authorization: Bearer <login token>.
Sign in using the existing POST /login endpoint with email and password.

The backend checks the authenticated user's UUID against the comma-separated ADMIN_USER_IDS server environment variable. An empty/missing list denies everyone. IDs, rather than email strings, prevent a deleted and re-created account from inheriting administrator access. The local .env is configured for the existing deferranti@me.com account. No account passwords or database records were changed.

Responses:
- 200: {"users":[{"id":"uuid","email":"...","created_at":"...","first_name":null,"last_name":null,"phone":null}]}
- 401: missing, invalid or expired login token
- 403: logged-in user is not an administrator
- 503: database read failed (no connection details exposed)

Password hashes are never selected by the list query. Admin responses use Cache-Control: no-store. This route only reads data.

## Local development

Start ShoppingCart with PORT=8081, APP_MODE=online and FEDEX_MODE=OFFLINE, then run gradlew.bat run. The database UI's local proxy defaults to http://127.0.0.1:8081. Override SHOPPING_CART_API_URL in the UI server environment when needed.

## Deployment

These changes are local until the backend is deployed. Set ADMIN_USER_IDS on the backend host to the selected existing account UUID before using the route. Keep DATABASE_URL only in the backend. Configure the UI host to proxy /api/login and /api/admin/users to ShoppingCart over HTTPS, stripping /api. The frontend contains no database connection or administrator secrets. The Vite development and preview servers already provide this proxy; a static deployment must configure its host separately.

## Verification

GetUsersHandlerTest covers missing/invalid login, ordinary users, absent admin configuration, permitted fields, null values and safe database errors. AdminUsersReadIntegrationTest is read-only and verifies the configured database connection and administrator account. Frontend browser tests cover login, bearer requests, search, sorting, refresh, TODO sections, sign-out, denied access, session expiry and narrow/wide layouts; UI responses are mocked except the real proxy rejection test. A real administrator sign-in still requires the account holder's password.
