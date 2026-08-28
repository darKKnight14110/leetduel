"use client";

import { ArrowClockwise, CheckCircle, Lightbulb, Sparkle } from "@phosphor-icons/react";
import { Client, type IMessage } from "@stomp/stompjs";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";
import { CodeEditor } from "@/components/editor/CodeEditor";
import { ErrorState, LoadingState } from "@/components/ui/PageState";
import { Button } from "@/components/ui/Button";
import { DifficultyBadge } from "@/components/problems/DifficultyBadge";
import {
  ApiError,
  createSubmission,
  getPracticeExplanation,
  getProblem,
  getProblemProgress,
  getSubmission,
  parseTestResults,
  requestPracticeWalkthrough,
  retryPracticeHint,
  UnauthorizedError,
  type ExplanationResponse,
  type Language,
  type ProblemDetail,
  type ProblemProgress,
  type SubmissionResponse,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

const POLL_INTERVAL_MS = 1500;
const MAX_POLLS = 40;
const configuredWsGatewayUrl = process.env.NEXT_PUBLIC_WS_GATEWAY_URL;

function getWsGatewayUrl() {
  if (configuredWsGatewayUrl) return configuredWsGatewayUrl;
  if (typeof window !== "undefined") {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws`;
  }
  return "ws://localhost:8090/ws";
}

const verdictColor: Record<string, string> = { ACCEPTED: "var(--success)", WRONG_ANSWER: "var(--danger)", TIME_LIMIT_EXCEEDED: "var(--danger)", RUNTIME_ERROR: "var(--danger)", COMPILE_ERROR: "var(--danger)", INTERNAL_ERROR: "var(--danger)" };

export default function ProblemDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [progress, setProgress] = useState<ProblemProgress | null>(null);
  const [language, setLanguage] = useState<Language>("PYTHON");
  const [drafts, setDrafts] = useState<Record<Language, string>>({ PYTHON: "", JAVA: "" });
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submission, setSubmission] = useState<SubmissionResponse | null>(null);
  const [explanation, setExplanation] = useState<ExplanationResponse | null>(null);
  const [explanationError, setExplanationError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const explanationTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const activeSubmissionId = useRef<string | null>(null);

  const refreshExplanation = useCallback((submissionId: string): Promise<ExplanationResponse | null> => {
    return getPracticeExplanation(submissionId).then((current) => {
      setExplanation(current);
      return current;
    }).catch((err) => {
      if (!(err instanceof ApiError && err.message.includes("404"))) setExplanationError("Coaching notes are still catching up. Try again shortly.");
      return null;
    });
  }, []);

  const startExplanationPolling = useCallback((submissionId: string) => {
    if (explanationTimer.current) clearInterval(explanationTimer.current);
    const poll = async () => {
      const current = await refreshExplanation(submissionId);
      const hintFinished = current && ["READY", "FAILED"].includes(current.hintStatus);
      const walkthroughFinished = current && current.walkthroughStatus !== "GENERATING";
      if (hintFinished && walkthroughFinished && explanationTimer.current) {
        clearInterval(explanationTimer.current);
        explanationTimer.current = null;
      }
    };
    void poll();
    explanationTimer.current = setInterval(() => { void poll(); }, 2500);
  }, [refreshExplanation]);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }
    getProblem(id).then((detail) => {
      setProblem(detail);
      setDrafts({ PYTHON: detail.languageStubs.PYTHON ?? "", JAVA: detail.languageStubs.JAVA ?? "" });
      return getProblemProgress(id).then(setProgress).catch(() => undefined);
    }).catch((err) => {
      if (err instanceof UnauthorizedError) {
        router.push("/login");
        return;
      }
      setLoadError(err instanceof ApiError ? err.message : "Could not load this problem.");
    });
  }, [id, router, retryToken]);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;
    const client = new Client({ brokerURL: getWsGatewayUrl(), connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 3000, debug: () => {}, onConnect: () => {
      client.subscribe("/user/queue/practice", (message: IMessage) => {
        try {
          const event = JSON.parse(message.body) as { submissionId?: string };
          if (event.submissionId && event.submissionId === activeSubmissionId.current) refreshExplanation(event.submissionId);
        } catch {
          setExplanationError("Received an unreadable coaching update. Refresh to recover.");
        }
      });
    } });
    client.activate();
    return () => { void client.deactivate(); };
  }, [refreshExplanation]);

  useEffect(() => () => {
    if (pollTimer.current) clearTimeout(pollTimer.current);
    if (explanationTimer.current) clearInterval(explanationTimer.current);
  }, []);

  useEffect(() => {
    const judgedSubmissionId = submission?.status === "JUDGED" ? submission.id : null;
    if (!judgedSubmissionId) return;
    activeSubmissionId.current = judgedSubmissionId;
    startExplanationPolling(judgedSubmissionId);
    return () => { if (explanationTimer.current) clearInterval(explanationTimer.current); };
  }, [submission, startExplanationPolling]);

  function pollSubmission(submissionId: string, attempt: number) {
    if (attempt >= MAX_POLLS) { setSubmitError("Still judging after a while. Try again shortly."); setSubmitting(false); return; }
    getSubmission(submissionId).then((current) => {
      setSubmission(current);
      if (current.status === "JUDGED") { setSubmitting(false); setProgress((value) => value ? { ...value, attemptedCount: value.attemptedCount + 1, lastVerdict: current.verdict, solved: value.solved || current.verdict === "ACCEPTED" } : value); return; }
      pollTimer.current = setTimeout(() => pollSubmission(submissionId, attempt + 1), POLL_INTERVAL_MS);
    }).catch((err) => { setSubmitError(err instanceof ApiError ? err.message : "Lost track of this submission."); setSubmitting(false); });
  }

  async function handleSubmit() {
    if (!problem) return;
    setSubmitting(true); setSubmitError(null); setExplanation(null); setExplanationError(null); setSubmission(null);
    try {
      const created = await createSubmission(problem.id, language, drafts[language]);
      activeSubmissionId.current = created.id;
      setSubmission(created);
      pollTimer.current = setTimeout(() => pollSubmission(created.id, 0), POLL_INTERVAL_MS);
    } catch (err) {
      if (err instanceof UnauthorizedError) { router.push("/login"); return; }
      setSubmitError(err instanceof ApiError ? err.message : "Could not submit. Is Submission Service running?"); setSubmitting(false);
    }
  }

  async function handleRetryHint() {
    if (!submission) return;
    setExplanationError(null);
    try {
      const current = await retryPracticeHint(submission.id);
      setExplanation(current);
      startExplanationPolling(submission.id);
    } catch { setExplanationError("Could not retry the hint yet."); }
  }

  async function handleWalkthrough() {
    if (!submission) return;
    setExplanationError(null);
    try {
      const current = await requestPracticeWalkthrough(submission.id);
      setExplanation(current);
      startExplanationPolling(submission.id);
    } catch { setExplanationError("Could not start the walkthrough yet."); }
  }

  if (loadError) return <AppShell><ErrorState message={loadError} onRetry={() => { setLoadError(null); setProblem(null); setRetryToken((value) => value + 1); }} /></AppShell>;
  if (!problem) return <AppShell><LoadingState label="Loading problem..." /></AppShell>;
  const results = submission ? parseTestResults(submission) : [];

  return <AppShell><main className="flex flex-1 flex-col"><div className="mx-auto w-full max-w-6xl px-6 pt-6"><Link href="/problems" className="text-sm text-fg-muted hover:text-fg">← Back to practice</Link></div><div className="mx-auto grid w-full max-w-6xl flex-1 gap-8 px-6 py-8 lg:grid-cols-[0.9fr_1.1fr]">
    <section><div className="flex flex-wrap items-center gap-3"><h1 className="text-2xl font-semibold text-fg">{problem.title}</h1><DifficultyBadge difficulty={problem.difficulty} />{progress?.solved && <span className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2.5 py-1 text-xs font-medium text-success"><CheckCircle size={14} weight="fill" /> Solved</span>}</div><p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-fg-muted">{problem.description}</p><h2 className="mt-8 text-sm font-medium text-fg">Sample test cases</h2><ul className="mt-3 flex flex-col gap-2">{problem.sampleTestCases.map((testCase) => <li key={testCase.ordinal} className="rounded-lg border border-border bg-surface p-3 font-mono text-xs text-fg-muted"><div>Input: {testCase.input}</div><div className="mt-1">Expected: {testCase.expectedOutput}</div></li>)}</ul></section>
    <section className="flex flex-col gap-4"><div role="tablist" aria-label="Programming language" className="flex gap-1 rounded-full border border-border bg-surface-2 p-1">{(["PYTHON", "JAVA"] as const).map((lang) => <button key={lang} type="button" role="tab" aria-selected={language === lang} onClick={() => setLanguage(lang)} className={`flex-1 rounded-full py-2 text-sm font-medium ${language === lang ? "bg-accent text-accent-ink" : "text-fg-muted hover:text-fg"}`}>{lang === "PYTHON" ? "Python" : "Java"}</button>)}</div><div><h2 className="mb-2 text-sm font-medium text-fg">Your solution</h2><CodeEditor value={drafts[language]} language={language} disabled={submitting} onChange={(value) => setDrafts((current) => ({ ...current, [language]: value }))} /></div><Button variant="primary" disabled={submitting || drafts[language].trim().length === 0} onClick={handleSubmit} className="w-full">{submitting ? <><ArrowClockwise className="animate-spin" size={18} weight="bold" /> <span className="ml-2">Judging</span></> : "Check solution"}</Button>{submitError && <p className="text-sm text-danger">{submitError}</p>}
      {submission && <div aria-live="polite" className="rounded-xl border border-border bg-surface p-4">{submission.status === "PENDING" ? <p className="text-sm text-fg-muted"><span>Judging...</span> <span className="text-fg-muted/80">Running your solution against the test suite.</span></p> : <><p className="text-sm font-medium" style={{ color: verdictColor[submission.verdict ?? ""] }}>{submission.verdict} <span className="text-fg-muted">({submission.testCasesPassed}/{submission.testCasesTotal})</span></p><ul className="mt-3 flex flex-col gap-2">{results.map((result) => <li key={result.ordinal} className="font-mono text-xs text-fg-muted">Test {result.ordinal + 1}: {result.status}{result.sample && result.expectedOutput !== null && <div className="mt-1 text-fg-muted/80">expected {result.expectedOutput}, got {result.actualOutput}</div>}</li>)}</ul></>}</div>}
      {submission?.status === "JUDGED" && <ExplanationPanel explanation={explanation} error={explanationError} onRetry={handleRetryHint} onWalkthrough={handleWalkthrough} />}
    </section>
  </div></main></AppShell>;
}

function ExplanationPanel({ explanation, error, onRetry, onWalkthrough }: { explanation: ExplanationResponse | null; error: string | null; onRetry: () => void; onWalkthrough: () => void }) {
  const hintReady = explanation?.hintStatus === "READY" && explanation.hint;
  const walkthroughReady = explanation?.walkthroughStatus === "READY" && explanation.walkthrough;
  return <section aria-labelledby="coach-heading" className="rounded-2xl border border-accent/25 bg-accent/5 p-5"><div className="flex items-center gap-2 text-accent"><Sparkle size={18} weight="fill" /><h2 id="coach-heading" className="text-sm font-semibold">Coach notes</h2></div>{error && <p className="mt-3 text-sm text-danger">{error}</p>}{!explanation || explanation.hintStatus === "QUEUED" || explanation.hintStatus === "GENERATING" ? <div role="status" className="mt-4 flex items-center gap-2 text-sm text-fg-muted"><ArrowClockwise className="animate-spin" size={16} />Preparing a focused hint...</div> : hintReady ? <div className="mt-4 space-y-4"><div><p className="text-sm leading-6 text-fg">{explanation.hint?.summary}</p><p className="mt-2 text-sm leading-6 text-fg-muted">{explanation.hint?.whatHappened}</p></div><div className="rounded-xl border border-accent/20 bg-surface p-4"><div className="flex items-center gap-2 text-accent"><Lightbulb size={17} weight="fill" /><p className="text-xs font-semibold uppercase tracking-[0.12em]">Next hint</p></div><p className="mt-2 text-sm leading-6 text-fg">{explanation.hint?.hint}</p></div><div className="flex flex-wrap gap-2">{explanation.hint?.concepts.map((concept) => <span key={concept} className="rounded-full bg-surface-2 px-2.5 py-1 text-xs text-fg-muted">{concept}</span>)}</div><p className="text-xs text-fg-muted">Complexity lens: {explanation.hint?.complexity}</p><div className="flex flex-wrap gap-3"><Button variant="ghost" onClick={onWalkthrough} disabled={explanation.walkthroughStatus === "GENERATING" || Boolean(walkthroughReady)}>{walkthroughReady ? "Walkthrough ready" : explanation.walkthroughStatus === "GENERATING" ? "Writing walkthrough..." : "Show full walkthrough"}</Button>{explanation.hintStatus === "FAILED" && <Button variant="ghost" onClick={onRetry}>Retry hint</Button>}</div>{walkthroughReady && <div className="border-t border-border pt-4"><p className="text-sm font-medium text-fg">Walkthrough</p><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-fg-muted">{explanation.walkthrough?.walkthrough}</p></div>}</div> : <div className="mt-4 flex items-center justify-between gap-3"><p className="text-sm text-fg-muted">The coach could not prepare notes right now.</p><Button variant="ghost" onClick={onRetry}>Retry hint</Button></div>}</section>;
}
