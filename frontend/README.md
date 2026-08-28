# LeetDuel frontend

The frontend is a Next.js 16 application for the LeetDuel practice and real-time match product. It is intentionally kept as a separate deployable image from the Spring services so the UI can scale and release independently from API workloads.

## Local development

From this directory:

```bash
npm install
npm run dev
```

The default development server runs at `http://localhost:3000`. Local API defaults point at Auth Service on `:8082`, API Gateway on `:8084`, and WS Gateway on `:8090`. Production builds use same-origin `/api` and derive the WebSocket endpoint from the browser host; explicit environment overrides remain available for local port-based development.

## Quality gates

```bash
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

Tests use Vitest, jsdom, React Testing Library, jest-dom, and user-event. The coverage focuses on user-visible states: loading, success, empty, partial data, retryable failure, authentication, language switching, and submission lockout.

## UI boundaries

- `app/` owns route-level screens and protected-page behavior.
- `components/` contains reusable shell, editor, state, and product components.
- `lib/` contains API clients, auth state, and browser transport helpers.
- Monaco is loaded client-side because the editor depends on browser APIs and should not increase the server-rendered route payload.

The backend remains the source of truth for problems, submissions, matches, profiles, and recommendations. The frontend composes bounded API reads and treats WebSocket messages as notifications that can be recovered through REST.
