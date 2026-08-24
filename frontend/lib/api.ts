// Talks directly to auth-service (localhost:8082 in dev) - there's no API
// Gateway yet (see docs/goals.md, still a later phase), so this is the one
// place that base URL is allowed to be hardcoded as a default. Every other
// component goes through this module rather than calling fetch directly.
const API_URL = process.env.NEXT_PUBLIC_AUTH_API_URL ?? "http://localhost:8082";

export type AuthResult = {
  accessToken: string;
  refreshToken: string;
};

export class ApiError extends Error {}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const data: unknown = await res.json().catch(() => null);
    throw new ApiError(extractMessage(data) ?? `Request failed (${res.status})`);
  }

  return res.json() as Promise<T>;
}

// GlobalExceptionHandler on the backend returns either {"error": "..."} for
// domain exceptions, or a field-name -> message map for validation
// failures (MethodArgumentNotValidException) - handle both shapes.
function extractMessage(data: unknown): string | null {
  if (!data || typeof data !== "object") return null;
  const record = data as Record<string, unknown>;
  if (typeof record.error === "string") return record.error;
  const firstFieldError = Object.values(record).find((v) => typeof v === "string");
  return typeof firstFieldError === "string" ? firstFieldError : null;
}

export function signup(username: string, email: string, password: string) {
  return post<AuthResult>("/auth/signup", { username, email, password });
}

export function login(identifier: string, password: string) {
  return post<AuthResult>("/auth/login", { identifier, password });
}

// Client-side decode only, for demo display (whose account is this,
// is the email verified) - never trust this for authorization, the
// signature is never checked here. Real authorization is whatever
// downstream service verifies the JWT with the shared secret.
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split(".")[1];
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), "=");
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}
