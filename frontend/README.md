# MiniMall Customer Frontend

Phase 1 customer storefront for MiniMall. A lightweight Vue 3 web frontend that
drives the backend microservice order flow: register → login → browse products →
view inventory → create order → view orders → mock pay → order becomes `PAID`.

## Tech Stack

- Vue 3 + TypeScript
- Vite (dev server / build)
- Element Plus (UI components)
- Pinia (state management)
- Vue Router (routing)
- Axios (HTTP client)

## Prerequisites

- Node.js 20.19+ or 22.12+ (developed on Node 24)
- A running MiniMall `api-gateway` (default `http://localhost:8080`)

## Setup

```bash
cd frontend
npm install
```

## Environment Variables

Copy the template and adjust if needed:

```bash
cp .env.example .env.local
```

| Variable | Purpose | Default |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Base URL of the api-gateway. All requests go through it. | `http://localhost:8080` |

Rules:

- The frontend calls **api-gateway only**, using canonical `/api/**` paths.
- Never point `VITE_API_BASE_URL` at a service port (e.g. 8101–8106).
- The frontend never calls `/internal/**` and never sends `X-User-Id` / `X-Username`.

## Commands

```bash
npm run dev         # start dev server (http://localhost:5173)
npm run build       # type-check (vue-tsc) + production build to dist/
npm run preview     # preview the production build
npm run type-check  # type-check only
```

## Backend Dependency

This frontend requires the MiniMall backend running locally. See the repository
root `README.md` for how to start the gateway and services, and
`docs/frontend-integration.md` for the API contract.
