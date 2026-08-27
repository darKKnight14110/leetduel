"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowClockwise } from "@phosphor-icons/react";
import { AppShell } from "@/components/layout/AppShell";
import { CodeEditor } from "@/components/editor/CodeEditor";
import { ErrorState, LoadingState } from "@/components/ui/PageState";
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
  const [drafts, setDrafts] = useState<Record<Language, string>>({ PYTHON: "", JAVA: "" });
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submission, setSubmission] = useState<SubmissionResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }
    getProblem(id)
      .then((detail) => {
        setProblem(detail);
        setDrafts({
          PYTHON: detail.languageStubs.PYTHON ?? "",
          JAVA: detail.languageStubs.JAVA ?? "",
        });
      })
      .catch((err) => {
        if (err instanceof UnauthorizedError) {
          router.push("/login");
          return;
        }
        setLoadError(err instanceof ApiError ? err.message : "Could not load this problem.");
      });
    // language intentionally excluded: this fetch runs once per problem.
  }, [id, router, retryToken]);

  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

  function handleLanguageChange(next: Language) {
    setLanguage(next);
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
      const created = await createSubmission(problem.id, language, drafts[language]);
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
      <AppShell>
        <ErrorState message={loadError} onRetry={() => { setLoadError(null); setProblem(null); setRetryToken((value) => value + 1); }} />
      </AppShell>
    );
  }

  if (!problem) {
    return (
      <AppShell>
        <LoadingState label="Loading problem..." />
      </AppShell>
    );
  }

  const results = submission ? parseTestResults(submission) : [];

  return (
    <AppShell>
      <main className="flex flex-1 flex-col">
        <div className="mx-auto w-full max-w-5xl px-6 pt-6">
          <Link href="/problems" className="text-sm text-fg-muted transition-colors hover:text-fg">
            ← Back to practice
          </Link>
        </div>

        <div className="mx-auto grid w-full max-w-5xl flex-1 gap-8 px-6 py-8 md:grid-cols-2">
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
          <div role="tablist" aria-label="Programming language" className="flex gap-1 rounded-full border border-border bg-surface-2 p-1">
            {(["PYTHON", "JAVA"] as const).map((lang) => (
              <button
                key={lang}
                type="button"
                role="tab"
                aria-selected={language === lang}
                onClick={() => handleLanguageChange(lang)}
                className={`flex-1 rounded-full py-2 text-sm font-medium transition-colors ${
                  language === lang ? "bg-accent text-accent-ink" : "text-fg-muted hover:text-fg"
                }`}
              >
                {lang === "PYTHON" ? "Python" : "Java"}
              </button>
            ))}
          </div>

          <div>
            <h2 className="mb-2 text-sm font-medium text-fg">Your solution</h2>
            <CodeEditor
              value={drafts[language]}
              language={language}
              disabled={submitting}
              onChange={(value) => setDrafts((current) => ({ ...current, [language]: value }))}
            />
          </div>

          <Button variant="primary" disabled={submitting || drafts[language].trim().length === 0} onClick={handleSubmit} className="w-full">
            {submitting ? <ArrowClockwise className="animate-spin" size={18} weight="bold" /> : "Check solution"}
          </Button>

          {submitError && <p className="text-sm text-danger">{submitError}</p>}

          {submission && (
            <div aria-live="polite" className="rounded-xl border border-border bg-surface p-4">
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
    </AppShell>
  );
}
