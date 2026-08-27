import { getAccessToken, clearAccessToken } from "./auth";

// Auth calls go straight to auth-service, not through the Gateway - the
// Gateway now exists (see GATEWAY_URL below) but its allowlist only
// exempts specific /auth/** paths from JWT verification; nothing here has
// a token yet to be verified in the first place, so there is no benefit to
// routing signup/login through it, and it would just add a hop. Every
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

// Everything below talks to the Gateway (localhost:8084 in dev), not
// directly to problem-service/submission-service. This isn't a style
// choice - it's required: Problem Service's public endpoints and every
// Submission Service endpoint trust an X-User-Id header that only
// JwtAuthWebFilter (inside the Gateway) ever sets, after verifying the
// JWT itself. Calling either service's port directly would arrive with no
// X-User-Id at all and 401/produce nonsensical requests. Auth calls above
// stay pointed at auth-service directly per that block's own comment;
// this is a second, separate base URL on purpose.
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_API_URL ?? "http://localhost:8084";

export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type Language = "PYTHON" | "JAVA";

export type ProblemSummary = {
  id: string;
  slug: string;
  title: string;
  difficulty: Difficulty;
};

export type ProblemParameter = {
  name: string;
  type: string;
};

export type SampleTestCase = {
  ordinal: number;
  input: string;
  expectedOutput: string;
};

export type ProblemDetail = {
  id: string;
  slug: string;
  title: string;
  description: string;
  difficulty: Difficulty;
  functionName: string;
  returnType: string;
  parameters: ProblemParameter[];
  languageStubs: Partial<Record<Language, string>>;
  sampleTestCases: SampleTestCase[];
};

export type PagedResponse<T> = {
  content: T[];
  totalPages: number;
  number: number;
};

export type SubmissionStatus = "PENDING" | "JUDGED";
export type Verdict =
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "TIME_LIMIT_EXCEEDED"
  | "RUNTIME_ERROR"
  | "COMPILE_ERROR"
  | "INTERNAL_ERROR";

export type TestCaseResult = {
  ordinal: number;
  status: string;
  runtimeMs: number;
  // Only populated on a failing case - see JudgeJobListener.toPayload on
  // the backend. All hidden test cases (not just samples) come back
  // through this same field once judged; the frontend doesn't currently
  // distinguish hidden-vs-sample here, matching what submission-service's
  // own read endpoint returns as-is.
  expectedOutput: string | null;
  actualOutput: string | null;
};

export type SubmissionResponse = {
  id: string;
  problemId: string;
  language: Language;
  sourceCode: string;
  status: SubmissionStatus;
  verdict: Verdict | null;
  testCasesPassed: number | null;
  testCasesTotal: number | null;
  // Raw JSON string on the wire, not a nested array - it is a JSONB
  // column (submissions.test_results) round-tripped through the entity
  // as a plain String (see Submission.java), not deserialized into a
  // typed object server-side. parseTestResults() below does the parsing
  // the backend deliberately does not do.
  testResults: string | null;
  createdAt: string;
  judgedAt: string | null;
};

export function parseTestResults(submission: SubmissionResponse): TestCaseResult[] {
  if (!submission.testResults) return [];
  try {
    return JSON.parse(submission.testResults) as TestCaseResult[];
  } catch {
    return [];
  }
}

// Thrown specifically for a missing/rejected token so callers can
// distinguish "not logged in" from a generic ApiError and redirect to
// /login, rather than just showing a raw error message on a protected page.
export class UnauthorizedError extends ApiError {}

async function authorizedFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  if (!token) throw new UnauthorizedError("Not logged in");

  const res = await fetch(`${GATEWAY_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });

  if (res.status === 401) {
    clearAccessToken();
    throw new UnauthorizedError("Session expired, please log in again");
  }
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => null);
    throw new ApiError(extractMessage(data) ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function listProblems(page = 0): Promise<PagedResponse<ProblemSummary>> {
  return authorizedFetch(`/problems?page=${page}`);
}

export function getProblem(id: string): Promise<ProblemDetail> {
  return authorizedFetch(`/problems/${id}`);
}

export function getProblemSummaries(ids: string[]): Promise<ProblemSummary[]> {
  return authorizedFetch(`/problems/summaries?ids=${ids.join(",")}`);
}

// matchId is undefined for practice-mode submissions (the existing
// /problems/[id] flow) and set when submitting from a live duel - mirrors
// CreateSubmissionRequest.matchId on the backend, null/absent either way
// funnels into the same optional field.
export function createSubmission(
  problemId: string,
  language: Language,
  sourceCode: string,
  matchId?: string,
): Promise<SubmissionResponse> {
  return authorizedFetch("/submissions", {
    method: "POST",
    body: JSON.stringify({ problemId, language, sourceCode, matchId: matchId ?? null }),
  });
}

export function getSubmission(id: string): Promise<SubmissionResponse> {
  return authorizedFetch(`/submissions/${id}`);
}

// join() returns 202 Accepted with no body (see QueueController) -
// authorizedFetch's res.json() would throw on an empty body, so this is a
// separate helper for endpoints that only need pass/fail, not a payload.
async function authorizedFetchVoid(path: string, options: RequestInit = {}): Promise<void> {
  const token = getAccessToken();
  if (!token) throw new UnauthorizedError("Not logged in");

  const res = await fetch(`${GATEWAY_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });

  if (res.status === 401) {
    clearAccessToken();
    throw new UnauthorizedError("Session expired, please log in again");
  }
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => null);
    throw new ApiError(extractMessage(data) ?? `Request failed (${res.status})`);
  }
}

export type QueueState = "NEVER_JOINED" | "WAITING" | "MATCHED" | "EXPIRED";

export type QueueStatusResponse = {
  state: QueueState;
  matchId: string | null;
};

export function joinQueue(): Promise<void> {
  return authorizedFetchVoid("/matchmaking/queue/join", { method: "POST" });
}

export function getQueueStatus(): Promise<QueueStatusResponse> {
  return authorizedFetch("/matchmaking/queue/status");
}

// 200 + body, not 204 - mirrors QueueController's own comment: a leave
// request can race the pairing sweep and come back MATCHED instead of
// cancelled, which the caller needs to be able to see.
export function leaveQueue(): Promise<QueueStatusResponse> {
  return authorizedFetch("/matchmaking/queue/leave", { method: "DELETE" });
}

export type MatchStatus = "IN_PROGRESS" | "COMPLETED";

export type MatchResponse = {
  matchId: string;
  player1Id: string;
  player2Id: string;
  problemId: string;
  timeLimitMs: number;
  player1ProgressPct: number;
  player2ProgressPct: number;
  status: MatchStatus;
  winnerId: string | null;
  isDraw: boolean;
  startedAt: string;
};

// Routed through the Gateway (Duel Service's /duels/{matchId}) - the
// duel page's initial-load / reconnect-recovery read. WS carries only
// live deltas after this first fetch.
export function getDuelMatch(matchId: string): Promise<MatchResponse> {
  return authorizedFetch(`/duels/${matchId}`);
}

export function getMatchHistory(userId: string, page = 0, size = 20): Promise<PagedResponse<MatchResponse>> {
  return authorizedFetch(`/duels/history/${userId}?page=${page}&size=${size}`);
}

// Public leaderboard reads (Phase 4) - Gateway's allowlist exempts these
// three exact paths from JWT verification, since leaderboard rankings are
// public data (see the Gateway's public-paths comment). No Authorization
// header on purpose: there may not even be a token (a logged-out visitor
// can view /leaderboard).
export type Board = "GLOBAL" | "WEEKLY" | "SEASON";

export type LeaderboardEntry = {
  userId: string;
  score: number;
  rank: number;
};

export type PublicIdentity = {
  userId: string;
  username: string;
};

export type LeaderboardTopResponse = {
  board: Board;
  entries: LeaderboardEntry[];
};

export type RankWindowResponse = {
  board: Board;
  userId: string;
  rank: number;
  entries: LeaderboardEntry[];
};

const LEADERBOARD_API_URL = process.env.NEXT_PUBLIC_LEADERBOARD_API_URL ?? GATEWAY_URL;

async function publicFetch<T>(path: string): Promise<T> {
  const res = await fetch(`${LEADERBOARD_API_URL}${path}`);
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => null);
    throw new ApiError(extractMessage(data) ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function getPublicIdentities(ids: string[]): Promise<PublicIdentity[]> {
  return publicFetchFromGateway(`/users/public-identities?ids=${ids.join(",")}`);
}

async function publicFetchFromGateway<T>(path: string): Promise<T> {
  const res = await fetch(`${GATEWAY_URL}${path}`);
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => null);
    throw new ApiError(extractMessage(data) ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function getLeaderboardTop(board: Board, limit = 50): Promise<LeaderboardTopResponse> {
  return publicFetch(`/leaderboard/top?board=${board}&limit=${limit}`);
}

export function getLeaderboardRankWindow(board: Board, userId: string, window = 5): Promise<RankWindowResponse> {
  return publicFetch(`/leaderboard/around?board=${board}&userId=${userId}&window=${window}`);
}

// Profile/stats reads (Phase 4) - authenticated, routed through the
// Gateway's existing /users route to user-service's new ProfileController.
export type ProfileStats = {
  userId: string;
  elo: number;
  duelsWon: number;
  duelsLost: number;
  duelsDrawn: number;
  avgOppEloWon: number | null;
  avgOppEloLost: number | null;
  avgOppEloDrawn: number | null;
};

export type EloHistoryPoint = {
  matchId: string;
  eloAfter: number;
  eloDelta: number;
  recordedAt: string;
};

export function getProfileStats(userId: string): Promise<ProfileStats> {
  return authorizedFetch(`/users/profile/${userId}`);
}

export function getEloHistory(userId: string): Promise<EloHistoryPoint[]> {
  return authorizedFetch(`/users/profile/${userId}/history`);
}
