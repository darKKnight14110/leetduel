import Link from "next/link";
import { AppShell } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/Button";

export default function NotFound() {
  return (
    <AppShell>
      <main className="flex flex-1 items-center justify-center px-6 py-24">
        <div className="max-w-md text-center">
          <p className="font-mono text-sm text-accent">404</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-fg">That page is not on the board.</h1>
          <p className="mt-4 text-sm leading-relaxed text-fg-muted">
            The link may be outdated, or the match has already moved on.
          </p>
          <div className="mt-8 flex justify-center gap-3">
            <Button href="/" variant="primary">Go home</Button>
            <Link href="/leaderboard" className="inline-flex items-center rounded-full border border-border-strong px-6 py-3 text-sm font-medium text-fg transition-colors hover:bg-surface">
              View leaderboard
            </Link>
          </div>
        </div>
      </main>
    </AppShell>
  );
}
