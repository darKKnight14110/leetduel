"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowClockwise } from "@phosphor-icons/react";
import { Logo } from "@/components/Logo";
import {
  getLeaderboardTop,
  getLeaderboardRankWindow,
  decodeJwtPayload,
  ApiError,
  type Board,
  type LeaderboardEntry,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

const TABS: { value: Board; label: string }[] = [
  { value: "GLOBAL", label: "Global" },
  { value: "WEEKLY", label: "This week" },
  { value: "SEASON", label: "This season" },
];

// Genuinely public page - unlike every other authenticated page in the app
// (duel, matchmaking, problems), this one has no getAccessToken() redirect
// to /login. Rankings are public data (see the Gateway's public-paths
// comment); only the "your position" panel below needs a token at all.
export default function LeaderboardPage() {
  const [board, setBoard] = useState<Board>("GLOBAL");
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selfRank, setSelfRank] = useState<{ rank: number; entries: LeaderboardEntry[] } | null>(null);

  // Lazy initializer, not setState-in-effect - same reasoning as the duel
  // page's selfId: decoding an already-present token is a pure computation
  // available at first render.
  const [selfId] = useState<string | null>(() => {
    const token = getAccessToken();
    if (!token) return null;
    const payload = decodeJwtPayload(token);
    return typeof payload?.sub === "string" ? payload.sub : null;
  });

  useEffect(() => {
    setLoading(true);
    setError(null);
    setSelfRank(null);

    getLeaderboardTop(board, 50)
      .then((res) => {
        setEntries(res.entries);
        // Only bother with the "around me" query if the viewer is logged
        // in AND isn't already visible in the top 50 - no point fetching a
        // second view of data already on screen.
        if (selfId && !res.entries.some((e) => e.userId === selfId)) {
          return getLeaderboardRankWindow(board, selfId, 3).then((window) =>
            setSelfRank({ rank: window.rank, entries: window.entries }),
          );
        }
        return undefined;
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Could not load the leaderboard.");
      })
      .finally(() => setLoading(false));
  }, [board, selfId]);

  return (
    <main className="flex flex-1 flex-col">
      <div className="mx-auto flex w-full max-w-3xl items-center justify-between px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
        <Link href="/matchmaking" className="text-sm text-fg-muted hover:text-fg">
          Play a duel
        </Link>
      </div>

      <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col px-6 py-10">
        <h1 className="text-2xl font-semibold text-fg">Leaderboard</h1>

        <div className="mt-6 flex gap-1 self-start rounded-full border border-border bg-surface-2 p-1">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => setBoard(tab.value)}
              className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                board === tab.value ? "bg-accent text-accent-ink" : "text-fg-muted hover:text-fg"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {error && <p className="mt-6 text-sm text-danger">{error}</p>}

        {loading ? (
          <div className="mt-10 flex justify-center">
            <ArrowClockwise className="animate-spin text-accent" size={24} weight="bold" />
          </div>
        ) : (
          <>
            <ol className="mt-6 flex flex-col overflow-hidden rounded-2xl border border-border">
              {entries.map((entry) => (
                <LeaderboardRow key={entry.userId} entry={entry} isSelf={entry.userId === selfId} />
              ))}
              {entries.length === 0 && (
                <li className="p-6 text-center text-sm text-fg-muted">
                  No one has played a duel {board === "GLOBAL" ? "yet" : "in this period yet"}.
                </li>
              )}
            </ol>

            {selfRank && (
              <div className="mt-6">
                <p className="mb-2 text-xs text-fg-muted">Your position</p>
                <ol className="flex flex-col overflow-hidden rounded-2xl border border-accent/40">
                  {selfRank.entries.map((entry) => (
                    <LeaderboardRow key={entry.userId} entry={entry} isSelf={entry.userId === selfId} />
                  ))}
                </ol>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}

function LeaderboardRow({ entry, isSelf }: { entry: LeaderboardEntry; isSelf: boolean }) {
  return (
    <li
      className={`flex items-center justify-between border-b border-border px-4 py-3 text-sm last:border-b-0 ${
        isSelf ? "bg-accent/10" : "bg-surface"
      }`}
    >
      <div className="flex items-center gap-4">
        <span className="w-8 text-right font-mono text-fg-muted">{entry.rank}</span>
        <span className={`font-mono ${isSelf ? "font-semibold text-fg" : "text-fg-muted"}`}>
          {entry.userId.slice(0, 8)}
          {isSelf && " (you)"}
        </span>
      </div>
      <span className="font-mono font-medium text-fg">{entry.score}</span>
    </li>
  );
}
