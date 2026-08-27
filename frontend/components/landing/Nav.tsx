"use client";

import { List, X } from "@phosphor-icons/react";
import Link from "next/link";
import { useState } from "react";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/Button";

export function Nav() {
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-bg/90 backdrop-blur-md">
      <div className="mx-auto flex min-h-16 max-w-7xl items-center justify-between px-6">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
        <nav aria-label="Primary navigation" className="hidden items-center gap-8 text-sm text-fg-muted md:flex">
          <Link href="#how-it-works" className="transition-colors hover:text-fg">
            How it works
          </Link>
          <Link href="#ratings" className="transition-colors hover:text-fg">
            Ratings
          </Link>
          <Link href="/leaderboard" className="transition-colors hover:text-fg">
            Leaderboard
          </Link>
        </nav>
        <Button href="/login" variant="primary">
          Log in
        </Button>
        <button
          type="button"
          aria-label={open ? "Close navigation" : "Open navigation"}
          aria-expanded={open}
          aria-controls="marketing-navigation"
          onClick={() => setOpen((current) => !current)}
          className="rounded-md p-2 text-fg-muted transition-colors hover:bg-surface hover:text-fg md:hidden"
        >
          {open ? <X size={22} weight="bold" /> : <List size={22} weight="bold" />}
        </button>
      </div>
      {open && (
        <nav id="marketing-navigation" aria-label="Mobile navigation" className="border-t border-border px-6 py-4 md:hidden">
          <div className="flex flex-col gap-1 text-sm">
            <Link href="#how-it-works" onClick={() => setOpen(false)} className="rounded-md px-3 py-3 text-fg-muted hover:bg-surface hover:text-fg">
              How it works
            </Link>
            <Link href="#ratings" onClick={() => setOpen(false)} className="rounded-md px-3 py-3 text-fg-muted hover:bg-surface hover:text-fg">
              Ratings
            </Link>
            <Link href="/leaderboard" onClick={() => setOpen(false)} className="rounded-md px-3 py-3 text-fg-muted hover:bg-surface hover:text-fg">
              Leaderboard
            </Link>
          </div>
        </nav>
      )}
    </header>
  );
}
