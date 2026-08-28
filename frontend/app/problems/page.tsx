"use client";

import { CheckCircle, Compass, Funnel, Sparkle } from "@phosphor-icons/react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";
import { ErrorState, LoadingState } from "@/components/ui/PageState";
import { DifficultyBadge } from "@/components/problems/DifficultyBadge";
import {
  ApiError,
  getPracticeOverview,
  listProblems,
  UnauthorizedError,
  type Difficulty,
  type PracticeOverview,
  type ProblemSummary,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

const difficulties: Array<"ALL" | Difficulty> = ["ALL", "EASY", "MEDIUM", "HARD"];

export default function ProblemsPage() {
  const router = useRouter();
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null);
  const [overview, setOverview] = useState<PracticeOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [difficulty, setDifficulty] = useState<"ALL" | Difficulty>("ALL");
  const [query, setQuery] = useState("");
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }
    Promise.allSettled([listProblems(), getPracticeOverview()]).then(([problemResult, overviewResult]) => {
      if (problemResult.status === "fulfilled") {
        setProblems(problemResult.value.content);
      } else if (problemResult.reason instanceof UnauthorizedError) {
        router.push("/login");
      } else {
        setError(problemResult.reason instanceof ApiError ? problemResult.reason.message : "Could not load problems.");
      }

      if (overviewResult.status === "fulfilled") {
        setOverview(overviewResult.value);
      } else if (overviewResult.reason instanceof UnauthorizedError) {
        router.push("/login");
      } else {
        setOverviewError("Progress and recommendations are temporarily unavailable.");
      }
    });
  }, [router, retryToken]);

  const filteredProblems = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (problems ?? []).filter((problem) => {
      const matchesDifficulty = difficulty === "ALL" || problem.difficulty === difficulty;
      const matchesQuery = !normalizedQuery || `${problem.title} ${problem.slug}`.toLowerCase().includes(normalizedQuery);
      return matchesDifficulty && matchesQuery;
    });
  }, [difficulty, problems, query]);

  if (error && !problems) {
    return <AppShell><ErrorState message={error} onRetry={() => { setProblems(null); setOverview(null); setError(null); setOverviewError(null); setRetryToken((value) => value + 1); }} /></AppShell>;
  }

  if (!problems) {
    return <AppShell><LoadingState label="Loading your practice space..." /></AppShell>;
  }

  const solvedIds = new Set(overview?.solvedProblemIds ?? []);
  const attemptedIds = new Set(overview?.attemptedProblemIds ?? []);

  return (
    <AppShell>
      <main className="flex flex-1 flex-col">
        <div className="mx-auto w-full max-w-6xl flex-1 px-6 py-10">
          <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
            <div>
              <p className="text-sm font-medium text-accent">Practice space</p>
              <h1 className="mt-2 text-3xl font-semibold tracking-tight text-fg">Build your next breakthrough.</h1>
              <p className="mt-2 max-w-xl text-sm leading-6 text-fg-muted">Short, focused reps with feedback that helps you understand the pattern, not just pass the test.</p>
            </div>
            <Link href="#catalog" className="inline-flex items-center gap-2 text-sm font-medium text-accent hover:text-accent-strong">Browse all problems <Compass size={18} /></Link>
          </div>

          {overview && <div className="mt-8 grid gap-3 sm:grid-cols-2"><StatCard label="Problems attempted" value={overview.attemptedCount} detail="Keep the streak honest." /><StatCard label="Problems solved" value={overview.solvedCount} detail="First accepted solutions stick." /></div>}
          {overviewError && <p className="mt-5 text-sm text-fg-muted">{overviewError}</p>}

          {overview && overview.recommendations.length > 0 && <section aria-labelledby="recommendations-heading" className="mt-10">
            <div className="flex items-end justify-between gap-4"><div><div className="flex items-center gap-2 text-accent"><Sparkle size={18} weight="fill" /><span className="text-sm font-medium">For your next session</span></div><h2 id="recommendations-heading" className="mt-2 text-xl font-semibold text-fg">Recommended for you</h2></div><span className="hidden text-xs text-fg-muted sm:inline">Based on your recent attempts</span></div>
            <div className="mt-4 grid gap-3 md:grid-cols-3">{overview.recommendations.map((recommendation) => <Link key={recommendation.problemId} href={`/problems/${recommendation.problemId}`} className="group rounded-xl border border-accent/30 bg-accent/5 p-4 transition-colors hover:border-accent"><div className="flex items-start justify-between gap-3"><h3 className="font-medium text-fg group-hover:text-accent-strong">{recommendation.title}</h3><DifficultyBadge difficulty={recommendation.difficulty} /></div><p className="mt-3 text-xs leading-5 text-fg-muted">{recommendation.reason}</p><div className="mt-3 flex flex-wrap gap-1.5">{recommendation.tags.slice(0, 3).map((tag) => <span key={tag} className="rounded-full bg-surface-2 px-2 py-1 text-[11px] text-fg-muted">{tag}</span>)}</div></Link>)}</div>
          </section>}

          <section id="catalog" aria-labelledby="catalog-heading" className="mt-12">
            <div className="flex flex-col gap-4 border-b border-border pb-5 md:flex-row md:items-end md:justify-between"><div><h2 id="catalog-heading" className="text-xl font-semibold text-fg">Problem catalog</h2><p className="mt-1 text-sm text-fg-muted">Choose a problem and make the next rep count.</p></div><div className="flex flex-col gap-2 sm:flex-row"><label className="sr-only" htmlFor="problem-search">Search problems</label><input id="problem-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search problems" className="rounded-full border border-border-strong bg-surface px-4 py-2 text-sm text-fg placeholder:text-fg-muted" /><label className="sr-only" htmlFor="difficulty-filter">Filter by difficulty</label><div className="relative"><Funnel aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-fg-muted" size={15} /><select id="difficulty-filter" value={difficulty} onChange={(event) => setDifficulty(event.target.value as "ALL" | Difficulty)} className="rounded-full border border-border-strong bg-surface py-2 pl-9 pr-8 text-sm text-fg"><option value="ALL">All levels</option>{difficulties.slice(1).map((level) => <option key={level} value={level}>{level[0] + level.slice(1).toLowerCase()}</option>)}</select></div></div></div>
            {filteredProblems.length === 0 ? <p className="mt-8 text-sm text-fg-muted">No problems match those filters.</p> : <ul className="mt-5 grid gap-3 md:grid-cols-2">{filteredProblems.map((problem) => { const solved = solvedIds.has(problem.id); const attempted = attemptedIds.has(problem.id); return <li key={problem.id}><Link href={`/problems/${problem.id}`} className="flex items-center justify-between gap-4 rounded-xl border border-border bg-surface px-5 py-4 transition-colors hover:border-border-strong"><div className="min-w-0"><div className="flex items-center gap-2">{solved && <CheckCircle aria-label="Solved" className="shrink-0 text-success" size={18} weight="fill" />}<span className="truncate font-medium text-fg">{problem.title}</span></div><p className="mt-1 text-xs text-fg-muted">{solved ? "Solved" : attempted ? "Attempted" : "Not started"}</p></div><DifficultyBadge difficulty={problem.difficulty} /></Link></li>; })}</ul>}
          </section>
        </div>
      </main>
    </AppShell>
  );
}

function StatCard({ label, value, detail }: { label: string; value: number; detail: string }) {
  return <div className="rounded-xl border border-border bg-surface p-5"><p className="text-xs font-medium uppercase tracking-[0.14em] text-fg-muted">{label}</p><p className="mt-3 text-3xl font-semibold text-fg">{value}</p><p className="mt-1 text-xs text-fg-muted">{detail}</p></div>;
}
