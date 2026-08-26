"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Logo } from "@/components/Logo";
import { DifficultyBadge } from "@/components/problems/DifficultyBadge";
import { listProblems, UnauthorizedError, ApiError, type ProblemSummary } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

// Client-rendered, not a server component fetching at request time - the
// only credential this page can use is the access token sitting in
// localStorage (see lib/auth.ts), which a server component has no access
// to. Every page under /problems follows this same shape for the same
// reason.
export default function ProblemsPage() {
  const router = useRouter();
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
      return;
    }
    listProblems()
      .then((page) => setProblems(page.content))
      .catch((err) => {
        if (err instanceof UnauthorizedError) {
          router.push("/login");
          return;
        }
        setError(err instanceof ApiError ? err.message : "Could not load problems. Is the Gateway running?");
      });
  }, [router]);

  return (
    <main className="flex flex-1 flex-col">
      <div className="mx-auto w-full max-w-3xl px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
      </div>

      <div className="mx-auto w-full max-w-3xl flex-1 px-6 py-10">
        <h1 className="text-2xl font-semibold text-fg">Problems</h1>

        {error && <p className="mt-6 text-sm text-danger">{error}</p>}

        {!error && !problems && <p className="mt-6 text-sm text-fg-muted">Loading...</p>}

        {problems && problems.length === 0 && (
          <p className="mt-6 text-sm text-fg-muted">No problems yet.</p>
        )}

        {problems && problems.length > 0 && (
          <ul className="mt-6 flex flex-col gap-3">
            {problems.map((p) => (
              <li key={p.id}>
                <Link
                  href={`/problems/${p.id}`}
                  className="flex items-center justify-between rounded-xl border border-border bg-surface px-5 py-4 transition-colors hover:border-border-strong"
                >
                  <span className="font-medium text-fg">{p.title}</span>
                  <DifficultyBadge difficulty={p.difficulty} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
