import { Logo } from "@/components/Logo";
import Link from "next/link";

export function Footer() {
  return (
    <footer className="mx-auto w-full max-w-7xl px-6 py-10">
      <div className="flex flex-col items-center justify-between gap-4 border-t border-border pt-8 md:flex-row">
        <Logo />
        <nav aria-label="Footer navigation" className="flex items-center gap-5 text-sm text-fg-muted">
          <Link href="/leaderboard" className="transition-colors hover:text-fg">Leaderboard</Link>
          <Link href="/login" className="transition-colors hover:text-fg">Log in</Link>
          <span>© {new Date().getFullYear()} LeetDuel</span>
        </nav>
      </div>
    </footer>
  );
}
