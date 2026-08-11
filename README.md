# ShoppingCart

## Database

The app uses in-memory repositories by default. To run against real services, set `APP_MODE=online`.

For local development, copy `.env.example` to `.env` and replace the placeholder values with your Neon credentials. The `.env` file is ignored by git.

For Neon, use the connection string from the dashboard. The app accepts either format:

```text
APP_MODE=online
DATABASE_URL=postgresql://neondb_owner:your-password@your-host/neondb?sslmode=require
```

or:

```text
APP_MODE=online
DATABASE_URL=jdbc:postgresql://your-host/neondb?sslmode=require
DATABASE_USER=neondb_owner
DATABASE_PASSWORD=your-password
```

When `APP_MODE=online`, the app creates these tables automatically:

- `users`
- `auth_tokens`
- `shopping_carts`
- `shopping_cart_products`

Run the app with:

```powershell
.\gradlew run
```

## Login Tester

Start the API, then open `frontend/login-tester.html` in your browser. The page can call `/register`, call `/login`, store the returned token in local storage, and call `/auth/status` with `Authorization: Bearer <token>`.
