"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowClockwise } from "@phosphor-icons/react";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/Button";
import {
  joinQueue,
  getQueueStatus,
  leaveQueue,
  UnauthorizedError,
  ApiError,
  type QueueState,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

// Polling, not a WebSocket push - same reasoning as the submission-judging
// poll on /problems/[id]: there is no push channel out of Matchmaking
// Service yet (that's the WS Gateway, Phase 3). 2s matches the backend
// sweep's own ~1s cadence closely enough to feel responsive without
// hammering the Gateway every second from every waiting client.
const POLL_INTERVAL_MS = 2000;

export default function MatchmakingPage() {
  const router = useRouter();
  const [state, setState] = useState<QueueState>("NEVER_JOINED");
  const [matchId, setMatchId] = useState<string | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const tickTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.push("/login");
    }
  }, [router]);

  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
      if (tickTimer.current) clearInterval(tickTimer.current);
    };
  }, []);

  function stopTimers() {
    if (pollTimer.current) {
      clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
    if (tickTimer.current) {
      clearInterval(tickTimer.current);
      tickTimer.current = null;
    }
  }

  function handleUnauthorized(err: unknown): boolean {
    if (err instanceof UnauthorizedError) {
      router.push("/login");
      return true;
    }
    return false;
  }

  function pollStatus() {
    getQueueStatus()
      .then((status) => {
        setState(status.state);
        setMatchId(status.matchId);
        if (status.state === "WAITING") {
          pollTimer.current = setTimeout(pollStatus, POLL_INTERVAL_MS);
        } else {
          stopTimers();
        }
      })
      .catch((err) => {
        if (handleUnauthorized(err)) return;
        stopTimers();
        setError(err instanceof ApiError ? err.message : "Lost track of the queue - is Matchmaking Service running?");
      });
  }

  async function handleJoin() {
    setBusy(true);
    setError(null);
    setMatchId(null);
    try {
      await joinQueue();
      setState("WAITING");
      setElapsedSeconds(0);
      tickTimer.current = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
      pollTimer.current = setTimeout(pollStatus, POLL_INTERVAL_MS);
    } catch (err) {
      if (handleUnauthorized(err)) return;
      setError(err instanceof ApiError ? err.message : "Could not join the queue. Is Matchmaking Service running?");
    } finally {
      setBusy(false);
    }
  }

  async function handleLeave() {
    setBusy(true);
    setError(null);
    stopTimers();
    try {
      const status = await leaveQueue();
      // A race with the pairing sweep means this can come back MATCHED
      // instead of cancelled - see QueueController's own comment on why
      // /leave returns 200 + body rather than a bare 204.
      setState(status.state);
      setMatchId(status.matchId);
    } catch (err) {
      if (handleUnauthorized(err)) return;
      setError(err instanceof ApiError ? err.message : "Could not leave the queue.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="flex flex-1 flex-col">
      <div className="mx-auto flex w-full max-w-3xl items-center justify-between px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
        <Link href="/problems" className="text-sm text-fg-muted hover:text-fg">
          Practice solo instead
        </Link>
      </div>

      <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col items-center px-6 py-16 text-center">
        <h1 className="text-2xl font-semibold text-fg">Ranked Duel</h1>
        <p className="mt-2 max-w-md text-sm text-fg-muted">
          Get matched against an opponent close to your rating and race them to the correct solution.
        </p>

        {error && <p className="mt-6 text-sm text-danger">{error}</p>}

        <div className="mt-10 flex w-full max-w-sm flex-col items-center gap-4 rounded-2xl border border-border bg-surface p-8">
          {state === "NEVER_JOINED" && (
            <>
              <p className="text-sm text-fg-muted">Ready when you are.</p>
              <Button variant="primary" disabled={busy} onClick={handleJoin} className="w-full">
                {busy ? <ArrowClockwise className="animate-spin" size={18} weight="bold" /> : "Find match"}
              </Button>
            </>
          )}

          {state === "WAITING" && (
            <>
              <ArrowClockwise className="animate-spin text-accent" size={28} weight="bold" />
              <p className="text-sm font-medium text-fg">Searching for an opponent...</p>
              <p className="text-xs text-fg-muted">
                {elapsedSeconds}s elapsed - your acceptable rating range widens the longer you wait.
              </p>
              <Button variant="ghost" disabled={busy} onClick={handleLeave} className="w-full">
                Cancel
              </Button>
            </>
          )}

          {state === "MATCHED" && matchId && (
            <>
              <p className="text-sm font-medium text-success">Match found.</p>
              <Button variant="primary" href={`/duel/${matchId}`} className="w-full">
                Enter duel
              </Button>
            </>
          )}

          {state === "EXPIRED" && (
            <>
              <p className="text-sm font-medium text-danger">No opponent found in time.</p>
              <Button variant="primary" disabled={busy} onClick={handleJoin} className="w-full">
                {busy ? <ArrowClockwise className="animate-spin" size={18} weight="bold" /> : "Try again"}
              </Button>
            </>
          )}
        </div>
      </div>
    </main>
  );
}
