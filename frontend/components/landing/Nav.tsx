import Link from "next/link";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/Button";

// Single line at desktop, 64px tall - see design-taste-frontend skill's
// Navigation height cap. One CTA only ("Log in" - the same label the hero
// and auth page use, so there's exactly one "start using the product"
// intent on the whole site).
export function Nav() {
  return (
    <header className="sticky top-0 z-40 h-16 border-b border-border bg-bg/80 backdrop-blur-md">
      <div className="mx-auto flex h-full max-w-7xl items-center justify-between px-6">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
        <nav className="hidden items-center gap-8 text-sm text-fg-muted md:flex">
          <Link href="#how-it-works" className="hover:text-fg">
            How it works
          </Link>
          <Link href="#ratings" className="hover:text-fg">
            Ratings
          </Link>
          <Link href="/leaderboard" className="hover:text-fg">
            Leaderboard
          </Link>
        </nav>
        <Button href="/login" variant="primary">
          Log in
        </Button>
      </div>
    </header>
  );
}
