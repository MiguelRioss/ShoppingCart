# Integration Guide

This guide explains how to use the ShoppingCart API from another frontend, backend, mobile app, or automation.

## Recommended Client Flow

1. Create a stable `sessionId` when the visitor first opens your app.
2. Store `sessionId` locally in the browser, app storage, or server session.
3. Save the cart with `POST /cart` whenever the user changes products.
4. Register or log in the user when needed.
5. Store the returned auth `token`.
6. Continue sending `Authorization: Bearer <token>` for logged-in requests.
7. Create checkout with `POST /checkout`.

## Session ID

The API uses `sessionId` to keep a guest cart before login.

Example browser code:

```js
function getSessionId() {
  const key = "shoppingCartSessionId";
  let sessionId = localStorage.getItem(key);

  if (!sessionId) {
    sessionId = `browser-${crypto.randomUUID()}`;
    localStorage.setItem(key, sessionId);
  }

  return sessionId;
}
```

## Fetch Helper

```js
const API_BASE_URL = "https://shoppingcart-4ohf.onrender.com";

async function apiFetch(path, { method = "GET", token, body } = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(data?.description || data?.message || `HTTP ${response.status}`);
  }

  return data;
}
```

## Save A Guest Cart

```js
await apiFetch("/cart", {
  method: "POST",
  body: {
    sessionId: getSessionId(),
    products: [
      {
        productId: 9278,
        quantityM2: 0.5
      }
    ]
  }
});
```

## Register And Keep The Cart

Pass the same `sessionId` during registration. The backend will connect that guest cart to the new user.

```js
const auth = await apiFetch("/register", {
  method: "POST",
  body: {
    firstName: "Jane",
    lastName: "Smith",
    email: "buyer@example.com",
    password: "password-123",
    phone: "+351 912 345 678",
    customerType: "private_customer",
    deliveryAddress: {
      addressLine1: "Street and house number",
      townOrCity: "Lisbon",
      postcode: "1000-001",
      country: "Portugal"
    },
    sameAsDeliveryAddress: true,
    sessionId: getSessionId()
  }
});

localStorage.setItem("shoppingCartToken", auth.token);
```

## Login And Keep The Cart

```js
const auth = await apiFetch("/login", {
  method: "POST",
  body: {
    email: "buyer@example.com",
    password: "password-123",
    sessionId: getSessionId()
  }
});

localStorage.setItem("shoppingCartToken", auth.token);
```

## Load The Cart

For a guest user:

```js
const cart = await apiFetch(`/cart?sessionId=${encodeURIComponent(getSessionId())}`);
```

For a logged-in user:

```js
const cart = await apiFetch("/cart", {
  token: localStorage.getItem("shoppingCartToken")
});
```

## Create Checkout

```js
const checkout = await apiFetch("/checkout", {
  method: "POST",
  token: localStorage.getItem("shoppingCartToken"),
  body: {
    sessionId: getSessionId(),
    successUrl: "https://your-app.example/success",
    cancelUrl: "https://your-app.example/cart"
  }
});

window.location.href = checkout.url;
```

## Shipping

Login is required for checkout. Shipping is calculated by the server using the saved
delivery address on the authenticated account. Update that address using the account
endpoint before checkout. Do not send `deliveryAddress` or `shippingCharge` to `/checkout`.

## Testing With Postman

Import this file into Postman:

```text
postman-shoppingcart-render.postman_collection.json
```

The collection automatically creates a unique email and session id when it runs.
