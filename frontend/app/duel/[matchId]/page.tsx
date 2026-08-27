"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { Client, type IMessage } from "@stomp/stompjs";
import { ArrowClockwise } from "@phosphor-icons/react";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/Button";
import {
  getDuelMatch,
  getProblem,
  createSubmission,
  getSubmission,
  decodeJwtPayload,
  UnauthorizedError,
  ApiError,
  type MatchResponse,
  type ProblemDetail,
  type Language,
  type SubmissionResponse,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

// Standalone, direct-connect - NOT proxied through the Gateway (see
// docs/goals.md's Phase 3 plan: the Gateway's ProxyWebFilter has no
// WebSocket-upgrade support). Falls back to the compose-default port if
// unset, same pattern as the Gateway/Auth base URLs in lib/api.ts.
const WS_GATEWAY_URL = process.env.NEXT_PUBLIC_WS_GATEWAY_URL ?? "ws://localhost:8090/ws";

const SUBMISSION_POLL_INTERVAL_MS = 1500;
const SUBMISSION_MAX_POLLS = 40;

// The WS Gateway relays raw JSON straight through from RabbitMQ (see
// RedisToStompRelay's comment on why) - there is no explicit "type" field
// on the wire, so messages are told apart by which fields are present.
// duel.progress carries progressPct; match.completed carries isDraw.
// match.created (also broadcast on this same topic) has neither and is
// ignored here - by the time this page has a matchId to subscribe with,
// the match already exists, fetched via the initial GET /duels/{matchId}.
type ProgressMessage = { matchId: string; userId: string; progressPct: number };
type CompletedMessage = {
  matchId: string;
  player1Id: string;
  player2Id: string;
  winnerId: string | null;
  isDraw: boolean;
};

function isProgressMessage(m: Record<string, unknown>): m is ProgressMessage {
  return typeof m.progressPct === "number";
}

function isCompletedMessage(m: Record<string, unknown>): m is CompletedMessage {
  return typeof m.isDraw === "boolean";
}

export default function DuelPage() {
  const { matchId } = useParams<{ matchId: string }>();
  const router = useRouter();

  const [match, setMatch] = useState<MatchResponse | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  // Lazy initializer, not setState-in-effect - decoding the already-present
  // token is a pure computation available at first render, not a
  // subscription to an external system, so there's nothing to synchronize.
  const [selfId] = useState<string | null>(() => {
    const token = getAccessToken();
    if (!token) return null;
    const payload = decodeJwtPayload(token);
    return typeof payload?.sub === "string" ? payload.sub : null;
  });

  const [language, setLanguage] = useState<Language>("PYTHON");
  const [code, setCode] = useState("");
  const [submission, setSubmission] = useState<SubmissionResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const stompClient = useRef<Client | null>(null);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }

    getDuelMatch(matchId)
      .then((m) => {
        setMatch(m);
        return getProblem(m.problemId);
      })
      .then((p) => {
        setProblem(p);
        setCode(p.languageStubs[language] ?? "");
      })
      .catch((err) => {
        if (err instanceof UnauthorizedError) {
          router.push("/login");
          return;
        }
        setLoadError(err instanceof ApiError ? err.message : "Could not load this match.");
      });
    // language intentionally excluded - same reasoning as /problems/[id]:
    // this effect fetches once on mount, language switching is handled
    // separately.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matchId, router]);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;

    const client = new Client({
      brokerURL: WS_GATEWAY_URL,
      // STOMP CONNECT frame header, not an HTTP Authorization header - a
      // browser can't set that on a native WS upgrade request. Validated
      // by StompAuthChannelInterceptor on the ws-gateway side.
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/duel/${matchId}`, (message: IMessage) => {
          const parsed = JSON.parse(message.body) as Record<string, unknown>;
          if (isProgressMessage(parsed)) {
            setMatch((prev) => {
              if (!prev) return prev;
              if (parsed.userId === prev.player1Id) {
                return { ...prev, player1ProgressPct: parsed.progressPct };
              }
              if (parsed.userId === prev.player2Id) {
                return { ...prev, player2ProgressPct: parsed.progressPct };
              }
              return prev;
            });
          } else if (isCompletedMessage(parsed)) {
            setMatch((prev) =>
              prev ? { ...prev, status: "COMPLETED", winnerId: parsed.winnerId, isDraw: parsed.isDraw } : prev,
            );
          }
        });
      },
    });
    client.activate();
    stompClient.current = client;

    return () => {
      client.deactivate();
    };
  }, [matchId]);

  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

  function handleLanguageChange(next: Language) {
    setLanguage(next);
    setCode(problem?.languageStubs[next] ?? "");
  }

  function pollSubmission(submissionId: string, attempt: number) {
    if (attempt >= SUBMISSION_MAX_POLLS) {
      setSubmitError("Still judging after a while - Judge Worker may be down.");
      setSubmitting(false);
      return;
    }
    getSubmission(submissionId)
      .then((sub) => {
        setSubmission(sub);
        if (sub.status === "JUDGED") {
          setSubmitting(false);
          return;
        }
        pollTimer.current = setTimeout(() => pollSubmission(submissionId, attempt + 1), SUBMISSION_POLL_INTERVAL_MS);
      })
      .catch((err) => {
        setSubmitError(err instanceof ApiError ? err.message : "Lost track of this submission.");
        setSubmitting(false);
      });
  }

  async function handleSubmit() {
    if (!problem) return;
    setSubmitting(true);
    setSubmitError(null);
    setSubmission(null);
    try {
      const created = await createSubmission(problem.id, language, code, matchId);
      setSubmission(created);
      pollTimer.current = setTimeout(() => pollSubmission(created.id, 0), SUBMISSION_POLL_INTERVAL_MS);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        router.push("/login");
        return;
      }
      setSubmitError(err instanceof ApiError ? err.message : "Could not submit.");
      setSubmitting(false);
    }
  }

  if (loadError) {
    return (
      <main className="flex flex-1 items-center justify-center px-6">
        <p className="text-sm text-danger">{loadError}</p>
      </main>
    );
  }

  if (!match || !problem) {
    return (
      <main className="flex flex-1 items-center justify-center px-6">
        <p className="text-sm text-fg-muted">Loading match...</p>
      </main>
    );
  }

  const isPlayer1 = selfId === match.player1Id;
  const yourProgress = isPlayer1 ? match.player1ProgressPct : match.player2ProgressPct;
  const opponentProgress = isPlayer1 ? match.player2ProgressPct : match.player1ProgressPct;
  const youWon = match.status === "COMPLETED" && !match.isDraw && match.winnerId === selfId;

  return (
    <main className="flex flex-1 flex-col">
      <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
      </div>

      <div className="mx-auto flex w-full max-w-5xl flex-col gap-2 px-6 pt-4">
        <div className="flex items-center gap-4">
          <div className="flex-1">
            <p className="text-xs text-fg-muted">You</p>
            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-2">
              <div className="h-full bg-accent transition-all" style={{ width: `${yourProgress}%` }} />
            </div>
          </div>
          <div className="flex-1">
            <p className="text-right text-xs text-fg-muted">Opponent</p>
            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-2">
              <div className="ml-auto h-full bg-danger transition-all" style={{ width: `${opponentProgress}%` }} />
            </div>
          </div>
        </div>

        {match.status === "COMPLETED" && (
          <p
            className={`text-center text-sm font-medium ${match.isDraw ? "text-fg" : youWon ? "text-success" : "text-danger"}`}
          >
            {match.isDraw ? "Draw." : youWon ? "You won." : "You lost."}
          </p>
        )}
      </div>

      <div className="mx-auto grid w-full max-w-5xl flex-1 gap-8 px-6 py-6 md:grid-cols-2">
        <section>
          <h1 className="text-xl font-semibold text-fg">{problem.title}</h1>
          <p className="mt-4 whitespace-pre-wrap text-sm text-fg-muted">{problem.description}</p>

          <h2 className="mt-6 text-sm font-medium text-fg">Sample test cases</h2>
          <ul className="mt-2 flex flex-col gap-2">
            {problem.sampleTestCases.map((tc) => (
              <li key={tc.ordinal} className="rounded-lg border border-border bg-surface p-3 font-mono text-xs text-fg-muted">
                <div>Input: {tc.input}</div>
                <div>Expected: {tc.expectedOutput}</div>
              </li>
            ))}
          </ul>
        </section>

        <section className="flex flex-col gap-4">
          <div className="flex gap-1 rounded-full border border-border bg-surface-2 p-1">
            {(["PYTHON", "JAVA"] as const).map((lang) => (
              <button
                key={lang}
                type="button"
                onClick={() => handleLanguageChange(lang)}
                className={`flex-1 rounded-full py-2 text-sm font-medium transition-colors ${
                  language === lang ? "bg-accent text-accent-ink" : "text-fg-muted hover:text-fg"
                }`}
              >
                {lang === "PYTHON" ? "Python" : "Java"}
              </button>
            ))}
          </div>

          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            spellCheck={false}
            disabled={match.status === "COMPLETED"}
            className="h-72 w-full resize-none rounded-xl border border-border-strong bg-surface-2 p-4 font-mono text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-accent disabled:opacity-50"
          />

          <Button
            variant="primary"
            disabled={submitting || match.status === "COMPLETED"}
            onClick={handleSubmit}
            className="w-full"
          >
            {submitting ? <ArrowClockwise className="animate-spin" size={18} weight="bold" /> : "Submit"}
          </Button>

          {submitError && <p className="text-sm text-danger">{submitError}</p>}

          {submission && (
            <div className="rounded-xl border border-border bg-surface p-4">
              {submission.status === "PENDING" ? (
                <p className="text-sm text-fg-muted">Judging...</p>
              ) : (
                <p className="text-sm font-medium text-fg">
                  {submission.verdict} ({submission.testCasesPassed}/{submission.testCasesTotal})
                </p>
              )}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
