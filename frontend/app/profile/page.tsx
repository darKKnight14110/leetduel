"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowClockwise } from "@phosphor-icons/react";
import { AppShell } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/Button";
import {
  getProfileStats,
  getEloHistory,
  getMatchHistory,
  getPublicIdentities,
  getProblemSummaries,
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
  const [matchError, setMatchError] = useState<string | null>(null);
  const [matchRetryToken, setMatchRetryToken] = useState(0);
  const [retryToken, setRetryToken] = useState(0);
  const [username, setUsername] = useState<string | null>(null);
  const [identities, setIdentities] = useState<Record<string, string>>({});
  const [problemTitles, setProblemTitles] = useState<Record<string, string>>({});
  const [loadedMatchRequest, setLoadedMatchRequest] = useState<string | null>(null);
  const matchRequestKey = `${matchPage}:${matchRetryToken}`;
  const matchesLoading = loadedMatchRequest !== matchRequestKey;

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
    getPublicIdentities([selfId])
      .then((resolvedIdentities) => {
        const identity = resolvedIdentities[0];
        if (identity) {
          setUsername(identity.username);
          setIdentities((current) => ({ ...current, [identity.userId]: identity.username }));
        }
      })
      .catch(() => undefined);
  }, [selfId, router, retryToken]);

  useEffect(() => {
    if (!selfId) return;
    getMatchHistory(selfId, matchPage)
      .then((res) => {
        setMatchError(null);
        setMatches(res.content);
        setTotalPages(res.totalPages);
        const opponentIds = res.content.map((match) => match.player1Id === selfId ? match.player2Id : match.player1Id);
        const problemIds = res.content.map((match) => match.problemId);
        const uniqueOpponentIds = [...new Set(opponentIds)];
        const uniqueProblemIds = [...new Set(problemIds)];
        return Promise.all([
          uniqueOpponentIds.length ? getPublicIdentities(uniqueOpponentIds).catch(() => []) : Promise.resolve([]),
          uniqueProblemIds.length ? getProblemSummaries(uniqueProblemIds).catch(() => []) : Promise.resolve([]),
        ]);
      })
      .then(([resolvedIdentities, summaries]) => {
        setIdentities((current) => ({
          ...current,
          ...Object.fromEntries(resolvedIdentities.map((identity) => [identity.userId, identity.username])),
        }));
        setProblemTitles((current) => ({
          ...current,
          ...Object.fromEntries(summaries.map((summary) => [summary.id, summary.title])),
        }));
      })
      .catch((err) => {
        if (err instanceof UnauthorizedError) {
          router.push("/login");
          return;
        }
        setMatchError(err instanceof ApiError ? err.message : "Could not load match history.");
      })
      .finally(() => {
        setLoadedMatchRequest(matchRequestKey);
      });
  }, [selfId, matchPage, router, matchRequestKey]);

  if (loadError) {
    return (
      <AppShell>
        <main className="flex flex-1 items-center justify-center px-6">
          <div className="flex max-w-sm flex-col items-center gap-4 text-center">
            <p className="text-sm text-danger">{loadError}</p>
            <Button variant="ghost" onClick={() => { setLoadError(null); setRetryToken((value) => value + 1); }}>
              Try again
            </Button>
          </div>
        </main>
      </AppShell>
    );
  }

  if (!stats) {
    return (
      <AppShell>
        <main className="flex flex-1 items-center justify-center px-6">
          <ArrowClockwise className="animate-spin text-accent" size={24} weight="bold" />
        </main>
      </AppShell>
    );
  }

  const totalMatches = stats.duelsWon + stats.duelsLost + stats.duelsDrawn;

  return (
    <AppShell>
      <main className="flex flex-1 flex-col">
        <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-8 px-6 py-10">
        {username && <p className="-mb-4 text-sm text-fg-muted">@{username}</p>}
        <section className="rounded-2xl border border-border bg-surface p-6">
          <div className="flex items-baseline justify-between">
            <h1 className="text-2xl font-semibold text-fg">{stats.elo}</h1>
            <span className="text-xs text-fg-muted">ELO</span>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-4 text-center text-sm">
            <div>
              <p className="font-semibold text-success">{stats.duelsWon}</p>
              <p className="text-xs text-fg-muted">Matches won</p>
            </div>
            <div>
              <p className="font-semibold text-danger">{stats.duelsLost}</p>
              <p className="text-xs text-fg-muted">Matches lost</p>
            </div>
            <div>
              <p className="font-semibold text-fg">{stats.duelsDrawn}</p>
              <p className="text-xs text-fg-muted">Draws</p>
            </div>
          </div>
          {totalMatches === 0 && (
            <p className="mt-4 text-center text-xs text-fg-muted">Find a match to start building a rating history.</p>
          )}
        </section>

        {history.length > 0 && (
          <section>
            <h2 className="mb-2 text-sm font-medium text-fg">Rating history</h2>
            <EloSparkline points={history} />
          </section>
        )}

        <section>
          <h2 className="mb-2 text-sm font-medium text-fg">Match history</h2>
          <ol className="flex flex-col overflow-hidden rounded-2xl border border-border">
            {matchesLoading && (
              <li className="p-6 text-center text-sm text-fg-muted">Loading match history...</li>
            )}
            {!matchesLoading && matchError && (
              <li className="flex flex-col items-center gap-3 p-6 text-center">
                <p className="text-sm text-danger">{matchError}</p>
                <Button variant="ghost" onClick={() => setMatchRetryToken((value) => value + 1)}>Try again</Button>
              </li>
            )}
            {!matchesLoading && !matchError && matches.map((m) => (
              <MatchHistoryRow
                key={m.matchId}
                match={m}
                selfId={selfId}
                opponentName={identities[m.player1Id === selfId ? m.player2Id : m.player1Id]}
                problemTitle={problemTitles[m.problemId]}
                eloDelta={history.find((point) => point.matchId === m.matchId)?.eloDelta}
              />
            ))}
            {!matchesLoading && !matchError && matches.length === 0 && (
              <li className="p-6 text-center text-sm text-fg-muted">No matches played yet.</li>
            )}
          </ol>
          {totalPages > 1 && (
            <div className="mt-3 flex justify-center gap-4 text-sm">
              <button
                type="button"
                aria-label="Previous match history page"
                disabled={matchPage === 0 || matchesLoading}
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
                aria-label="Next match history page"
                disabled={matchPage >= totalPages - 1 || matchesLoading}
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
    </AppShell>
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
    const x = points.length === 1 ? width / 2 : (i / (points.length - 1)) * width;
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

function MatchHistoryRow({
  match,
  selfId,
  opponentName,
  problemTitle,
  eloDelta,
}: {
  match: MatchResponse;
  selfId: string | null;
  opponentName?: string;
  problemTitle?: string;
  eloDelta?: number;
}) {
  const youWon = match.status === "COMPLETED" && !match.isDraw && match.winnerId === selfId;
  const youLost = match.status === "COMPLETED" && !match.isDraw && match.winnerId !== null && match.winnerId !== selfId;
  const resultLabel = match.status !== "COMPLETED" ? "In progress" : match.isDraw ? "Draw" : youWon ? "Won" : "Lost";
  const resultColor = youWon ? "text-success" : youLost ? "text-danger" : "text-fg-muted";

  return (
    <li className="flex items-center justify-between gap-4 border-b border-border bg-surface px-4 py-3 text-sm last:border-b-0">
      <div className="min-w-0">
        <Link href={`/problems/${match.problemId}`} className="block truncate font-medium text-fg transition-colors hover:text-accent">
          {problemTitle ?? `Problem ${match.problemId.slice(0, 8)}`}
        </Link>
        <p className="mt-1 truncate text-xs text-fg-muted">
          vs {opponentName ?? `Player ${match.player1Id === selfId ? match.player2Id.slice(0, 8) : match.player1Id.slice(0, 8)}`} · {new Date(match.startedAt).toLocaleDateString()}
        </p>
      </div>
      <div className="shrink-0 text-right">
        <p className={`font-medium ${resultColor}`}>{resultLabel}</p>
        {eloDelta !== undefined && match.status === "COMPLETED" && (
          <p className={`mt-1 font-mono text-xs ${eloDelta >= 0 ? "text-success" : "text-danger"}`}>
            {eloDelta >= 0 ? "+" : ""}{eloDelta} ELO
          </p>
        )}
      </div>
    </li>
  );
}
