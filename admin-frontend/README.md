# MiniMall Admin Frontend

The MiniMall **admin platform** — an operations console for staff, completely
independent from the customer storefront (`../frontend`). It has its own
`package.json`, build, router, layout, API client, and state. The two frontends
never share a browser application entry.

## Stack

Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios + Vitest.

## Gateway-only boundary (hard rule)

- All requests go through **api-gateway only** (`VITE_API_BASE_URL`, default
  `http://localhost:8080`). Never call a service port (8101–8106) and never call
  `/internal/**`.
- The browser must **never** send `X-User-Id`, `X-Username`, or `X-User-Role`.
  The gateway injects trusted identity after validating the JWT and strips any
  browser-forged values.
- `401` clears the token and redirects to `/login`; `403` redirects to `/403`;
  `429` shows a friendly rate-limit message.

## Routes

| Path             | Page                       |
| ---------------- | -------------------------- |
| `/login`         | Admin login (guest only)   |
| `/`              | Redirects to `/products`   |
| `/products`      | Product management         |
| `/inventories`   | Inventory management       |
| `/orders`        | Order management           |
| `/payments`      | Payment management         |
| `/notifications` | Notification logs          |
| `/audit-logs`    | Admin operation audit logs |

Protected routes verify the admin session via `GET /api/admin/me`.

## Develop

```bash
npm install
cp .env.example .env.local   # adjust VITE_API_BASE_URL if needed
npm run dev                  # http://localhost:5174
```

## Build / check / test

```bash
npm run build        # vue-tsc type-check + vite production build
npm run type-check   # types only
npm run test         # vitest
```
