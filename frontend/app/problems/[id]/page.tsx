"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowClockwise } from "@phosphor-icons/react";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/Button";
import { DifficultyBadge } from "@/components/problems/DifficultyBadge";
import {
  getProblem,
  createSubmission,
  getSubmission,
  parseTestResults,
  UnauthorizedError,
  ApiError,
  type ProblemDetail,
  type Language,
  type SubmissionResponse,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

// Polling, not a WebSocket/SSE push - Judge Worker publishes its result
// onto RabbitMQ, not to this frontend directly, and there is deliberately
// no push channel from submission-service to a browser yet (that's a
// duel-mode/live-match concern from a later phase, not single-player
// practice). 1.5s balances "feels responsive" against hammering the
// Gateway; MAX_POLLS bounds it so a stuck judge job (Judge Worker down,
// RabbitMQ unreachable) fails the UI instead of polling forever.
const POLL_INTERVAL_MS = 1500;
const MAX_POLLS = 40;

const verdictColor: Record<string, string> = {
  ACCEPTED: "var(--success)",
  WRONG_ANSWER: "var(--danger)",
  TIME_LIMIT_EXCEEDED: "var(--danger)",
  RUNTIME_ERROR: "var(--danger)",
  COMPILE_ERROR: "var(--danger)",
  INTERNAL_ERROR: "var(--danger)",
};

export default function ProblemDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [language, setLanguage] = useState<Language>("PYTHON");
  const [code, setCode] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submission, setSubmission] = useState<SubmissionResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }
    getProblem(id)
      .then((detail) => {
        setProblem(detail);
        setCode(detail.languageStubs[language] ?? "");
      })
      .catch((err) => {
        if (err instanceof UnauthorizedError) {
          router.push("/login");
          return;
        }
        setLoadError(err instanceof ApiError ? err.message : "Could not load this problem.");
      });
    // language intentionally excluded - this effect only fetches the
    // problem once on mount; switching languages is handled by
    // handleLanguageChange below so it doesn't refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, router]);

  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

  // Switching languages resets the editor to that language's stub rather
  // than trying to preserve in-progress edits across a language switch -
  // there is no shared AST between a Python and a Java solution to
  // translate between, so "keep what you typed" would just leave stale,
  // wrong-language text sitting in the box. Simplest correct behavior for
  // v1; a real product would at least warn before discarding edits.
  function handleLanguageChange(next: Language) {
    setLanguage(next);
    setCode(problem?.languageStubs[next] ?? "");
  }

  // A plain function, not useCallback - it recurses into itself via
  // setTimeout, and a memoized self-reference is exactly what
  // react-hooks/immutability flags as unsafe (the closure could go stale
  // across renders if this were memoized with changing deps). It has
  // nothing to memoize for anyway - nobody downstream needs referential
  // stability, it is only ever called from within this component.
  function pollSubmission(submissionId: string, attempt: number) {
    if (attempt >= MAX_POLLS) {
      setSubmitError("Still judging after a while - Judge Worker may be down. Try again shortly.");
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
        pollTimer.current = setTimeout(() => pollSubmission(submissionId, attempt + 1), POLL_INTERVAL_MS);
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
      const created = await createSubmission(problem.id, language, code);
      setSubmission(created);
      pollTimer.current = setTimeout(() => pollSubmission(created.id, 0), POLL_INTERVAL_MS);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        router.push("/login");
        return;
      }
      setSubmitError(err instanceof ApiError ? err.message : "Could not submit. Is Submission Service running?");
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

  if (!problem) {
    return (
      <main className="flex flex-1 items-center justify-center px-6">
        <p className="text-sm text-fg-muted">Loading...</p>
      </main>
    );
  }

  const results = submission ? parseTestResults(submission) : [];

  return (
    <main className="flex flex-1 flex-col">
      <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
        <Link href="/problems" className="text-sm text-fg-muted hover:text-fg">
          All problems
        </Link>
      </div>

      <div className="mx-auto grid w-full max-w-5xl flex-1 gap-8 px-6 py-10 md:grid-cols-2">
        <section>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-semibold text-fg">{problem.title}</h1>
            <DifficultyBadge difficulty={problem.difficulty} />
          </div>
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

          {/* Plain textarea, not Monaco - a real explicit trade-off, not a
              silent one. The Phase 1 plan's function-signature-harness
              decision was already the big scope call for this phase;
              wiring Monaco (syntax highlighting, language server hookup)
              is a genuinely separate, sizable frontend task on its own,
              not something to fold in silently while extending the
              frontend to exercise this backend. Revisit as a dedicated
              follow-up if this needs to feel like a real editor. */}
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            spellCheck={false}
            className="h-72 w-full resize-none rounded-xl border border-border-strong bg-surface-2 p-4 font-mono text-sm text-fg outline-none focus:border-accent focus:ring-1 focus:ring-accent"
          />

          <Button variant="primary" disabled={submitting} onClick={handleSubmit} className="w-full">
            {submitting ? <ArrowClockwise className="animate-spin" size={18} weight="bold" /> : "Submit"}
          </Button>

          {submitError && <p className="text-sm text-danger">{submitError}</p>}

          {submission && (
            <div className="rounded-xl border border-border bg-surface p-4">
              {submission.status === "PENDING" ? (
                <p className="text-sm text-fg-muted">Judging...</p>
              ) : (
                <>
                  <p className="text-sm font-medium" style={{ color: verdictColor[submission.verdict ?? ""] }}>
                    {submission.verdict} ({submission.testCasesPassed}/{submission.testCasesTotal})
                  </p>
                  <ul className="mt-3 flex flex-col gap-2">
                    {results.map((r) => (
                      <li key={r.ordinal} className="font-mono text-xs text-fg-muted">
                        Test {r.ordinal + 1}: {r.status}
                        {r.expectedOutput !== null && (
                          <div className="mt-1 text-fg-muted/80">
                            expected {r.expectedOutput}, got {r.actualOutput}
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
