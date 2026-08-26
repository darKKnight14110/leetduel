// Access token persistence for the judge-loop pages (/problems, /problems/[id]).
// AuthCard previously kept the token only in component state, which is fine
// for a single demo card but breaks the moment you navigate to a second
// page - there's nothing left to read the token from. localStorage (not a
// cookie, not React context) is the simplest thing that survives a full
// page navigation and a refresh without adding a state-management
// dependency or a server-side session store neither exists nor is needed
// here: this frontend has no backend of its own, every real request goes
// straight to the Gateway with this token as a bearer credential.
//
// Trade-off named, not hidden: localStorage is readable by any script on
// the page (XSS-exposed), unlike an httpOnly cookie. Accepted for this
// scope because there is no server component here to SET an httpOnly
// cookie in the first place - Gateway is a pure JSON API, not a
// cookie-issuing origin for this frontend. Revisit if this ever needs to
// be genuinely production-hardened.
const STORAGE_KEY = "leetduel.accessToken";

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(STORAGE_KEY);
}

export function setAccessToken(token: string): void {
  window.localStorage.setItem(STORAGE_KEY, token);
}

export function clearAccessToken(): void {
  window.localStorage.removeItem(STORAGE_KEY);
}
