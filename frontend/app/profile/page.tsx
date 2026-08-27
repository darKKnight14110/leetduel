"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowClockwise } from "@phosphor-icons/react";
import { Logo } from "@/components/Logo";
import {
  getProfileStats,
  getEloHistory,
  getMatchHistory,
  decodeJwtPayload,
  UnauthorizedError,
  ApiError,
  type ProfileStats,
  type EloHistoryPoint,
  type MatchResponse,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

// Self-profile only (no /profile/[userId] route yet) - see the Phase 4
// plan's "Out of scope" section. Authenticated like matchmaking/duel, not
// public like /leaderboard.
export default function ProfilePage() {
  const router = useRouter();
  const [selfId] = useState<string | null>(() => {
    const token = getAccessToken();
    if (!token) return null;
    const payload = decodeJwtPayload(token);
    return typeof payload?.sub === "string" ? payload.sub : null;
  });

  const [stats, setStats] = useState<ProfileStats | null>(null);
  const [history, setHistory] = useState<EloHistoryPoint[]>([]);
  const [matches, setMatches] = useState<MatchResponse[]>([]);
  const [matchPage, setMatchPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!selfId) {
      router.push("/login");
      return;
    }

    Promise.all([getProfileStats(selfId), getEloHistory(selfId)])
      .then(([statsRes, historyRes]) => {
        setStats(statsRes);
        setHistory(historyRes);
      })
      .catch((err) => {
        if (err instanceof UnauthorizedError) {
          router.push("/login");
          return;
        }
        setLoadError(err instanceof ApiError ? err.message : "Could not load your profile.");
      });
  }, [selfId, router]);

  useEffect(() => {
    if (!selfId) return;
    getMatchHistory(selfId, matchPage)
      .then((res) => {
        setMatches(res.content);
        setTotalPages(res.totalPages);
      })
      .catch(() => {
        // Match history is secondary to the stats panel above - a failure
        // here shouldn't blank out a page that otherwise loaded fine.
      });
  }, [selfId, matchPage]);

  if (loadError) {
    return (
      <main className="flex flex-1 items-center justify-center px-6">
        <p className="text-sm text-danger">{loadError}</p>
      </main>
    );
  }

  if (!stats) {
    return (
      <main className="flex flex-1 items-center justify-center px-6">
        <ArrowClockwise className="animate-spin text-accent" size={24} weight="bold" />
      </main>
    );
  }

  const totalDuels = stats.duelsWon + stats.duelsLost + stats.duelsDrawn;

  return (
    <main className="flex flex-1 flex-col">
      <div className="mx-auto flex w-full max-w-3xl items-center justify-between px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
        <Link href="/leaderboard" className="text-sm text-fg-muted hover:text-fg">
          Leaderboard
        </Link>
      </div>

      <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-8 px-6 py-10">
        <section className="rounded-2xl border border-border bg-surface p-6">
          <div className="flex items-baseline justify-between">
            <h1 className="text-2xl font-semibold text-fg">{stats.elo}</h1>
            <span className="text-xs text-fg-muted">ELO</span>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-4 text-center text-sm">
            <div>
              <p className="font-semibold text-success">{stats.duelsWon}</p>
              <p className="text-xs text-fg-muted">Won</p>
            </div>
            <div>
              <p className="font-semibold text-danger">{stats.duelsLost}</p>
              <p className="text-xs text-fg-muted">Lost</p>
            </div>
            <div>
              <p className="font-semibold text-fg">{stats.duelsDrawn}</p>
              <p className="text-xs text-fg-muted">Drawn</p>
            </div>
          </div>
          {totalDuels === 0 && (
            <p className="mt-4 text-center text-xs text-fg-muted">Play a duel to start building a rating history.</p>
          )}
        </section>

        {history.length > 1 && (
          <section>
            <h2 className="mb-2 text-sm font-medium text-fg">Rating history</h2>
            <EloSparkline points={history} />
          </section>
        )}

        <section>
          <h2 className="mb-2 text-sm font-medium text-fg">Match history</h2>
          <ol className="flex flex-col overflow-hidden rounded-2xl border border-border">
            {matches.map((m) => (
              <MatchHistoryRow key={m.matchId} match={m} selfId={selfId} />
            ))}
            {matches.length === 0 && (
              <li className="p-6 text-center text-sm text-fg-muted">No duels played yet.</li>
            )}
          </ol>
          {totalPages > 1 && (
            <div className="mt-3 flex justify-center gap-4 text-sm">
              <button
                type="button"
                disabled={matchPage === 0}
                onClick={() => setMatchPage((p) => p - 1)}
                className="text-fg-muted hover:text-fg disabled:opacity-40"
              >
                Previous
              </button>
              <span className="text-fg-muted">
                {matchPage + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={matchPage >= totalPages - 1}
                onClick={() => setMatchPage((p) => p + 1)}
                className="text-fg-muted hover:text-fg disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}

// Hand-rolled SVG, not a charting library - consistent with this project's
// existing precedent of deliberately scoping down secondary visuals (see
// the plain-textarea-instead-of-Monaco trade-off named in docs/goals.md).
function EloSparkline({ points }: { points: EloHistoryPoint[] }) {
  const width = 600;
  const height = 120;
  const values = points.map((p) => p.eloAfter);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;

  const coords = points.map((p, i) => {
    const x = (i / (points.length - 1)) * width;
    const y = height - ((p.eloAfter - min) / range) * height;
    return `${x},${y}`;
  });

  return (
    <div className="rounded-2xl border border-border bg-surface p-4">
      <svg viewBox={`0 0 ${width} ${height}`} className="h-24 w-full" preserveAspectRatio="none">
        <polyline points={coords.join(" ")} fill="none" stroke="var(--color-accent, #6366f1)" strokeWidth={2} />
      </svg>
      <div className="mt-1 flex justify-between text-xs text-fg-muted">
        <span>{min}</span>
        <span>{max}</span>
      </div>
    </div>
  );
}

function MatchHistoryRow({ match, selfId }: { match: MatchResponse; selfId: string | null }) {
  const youWon = match.status === "COMPLETED" && !match.isDraw && match.winnerId === selfId;
  const youLost = match.status === "COMPLETED" && !match.isDraw && match.winnerId !== null && match.winnerId !== selfId;
  const resultLabel = match.status !== "COMPLETED" ? "In progress" : match.isDraw ? "Draw" : youWon ? "Won" : "Lost";
  const resultColor = youWon ? "text-success" : youLost ? "text-danger" : "text-fg-muted";

  return (
    <li className="flex items-center justify-between border-b border-border bg-surface px-4 py-3 text-sm last:border-b-0">
      <span className="font-mono text-fg-muted">{new Date(match.startedAt).toLocaleDateString()}</span>
      <span className={`font-medium ${resultColor}`}>{resultLabel}</span>
    </li>
  );
}
