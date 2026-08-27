"use client";

import { List, X } from "@phosphor-icons/react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import type { ReactNode } from "react";
import { Logo } from "@/components/Logo";
import { clearAccessToken, getAccessToken } from "@/lib/auth";

const links = [
  { href: "/problems", label: "Practice" },
  { href: "/matchmaking", label: "Find a match" },
  { href: "/leaderboard", label: "Leaderboard" },
  { href: "/profile", label: "Profile" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const loggedIn = Boolean(getAccessToken());

  function closeMenu() {
    setOpen(false);
  }

  function logout() {
    clearAccessToken();
    closeMenu();
    router.push("/");
  }

  return (
    <div className="flex min-h-full flex-col">
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-accent px-4 py-2 font-medium text-accent-ink focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-border bg-bg/90 backdrop-blur-md">
        <div className="mx-auto flex min-h-16 w-full max-w-7xl items-center justify-between px-6">
          <Link href="/" aria-label="LeetDuel home" onClick={closeMenu}>
            <Logo />
          </Link>

          <nav aria-label="Primary navigation" className="hidden items-center gap-7 text-sm md:flex">
            {links.map((link) => (
              <NavLink key={link.href} link={link} pathname={pathname} />
            ))}
            {loggedIn ? (
              <button type="button" onClick={logout} className="text-fg-muted transition-colors hover:text-fg">
                Log out
              </button>
            ) : (
              <Link href="/login" className="text-fg-muted transition-colors hover:text-fg">
                Log in
              </Link>
            )}
          </nav>

          <button
            type="button"
            aria-label={open ? "Close navigation" : "Open navigation"}
            aria-expanded={open}
            aria-controls="mobile-navigation"
            onClick={() => setOpen((current) => !current)}
            className="rounded-md p-2 text-fg-muted transition-colors hover:bg-surface hover:text-fg md:hidden"
          >
            {open ? <X size={22} weight="bold" /> : <List size={22} weight="bold" />}
          </button>
        </div>

        {open && (
          <nav id="mobile-navigation" aria-label="Mobile navigation" className="border-t border-border px-6 py-4 md:hidden">
            <div className="mx-auto flex w-full max-w-7xl flex-col gap-1">
              {links.map((link) => (
                <NavLink key={link.href} link={link} pathname={pathname} onClick={closeMenu} mobile />
              ))}
              {loggedIn ? (
                <button
                  type="button"
                  onClick={logout}
                  className="rounded-md px-3 py-3 text-left text-sm text-fg-muted transition-colors hover:bg-surface hover:text-fg"
                >
                  Log out
                </button>
              ) : (
                <Link
                  href="/login"
                  onClick={closeMenu}
                  className="rounded-md px-3 py-3 text-sm text-fg-muted transition-colors hover:bg-surface hover:text-fg"
                >
                  Log in
                </Link>
              )}
            </div>
          </nav>
        )}
      </header>
      <div id="main-content" className="flex min-h-0 flex-1 flex-col">
        {children}
      </div>
    </div>
  );
}

function NavLink({
  link,
  pathname,
  onClick,
  mobile = false,
}: {
  link: (typeof links)[number];
  pathname: string;
  onClick?: () => void;
  mobile?: boolean;
}) {
  const active = pathname === link.href || pathname.startsWith(`${link.href}/`);
  return (
    <Link
      href={link.href}
      onClick={onClick}
      aria-current={active ? "page" : undefined}
      className={
        mobile
          ? `rounded-md px-3 py-3 text-sm transition-colors hover:bg-surface ${active ? "bg-surface text-fg" : "text-fg-muted"}`
          : `transition-colors hover:text-fg ${active ? "font-medium text-fg" : "text-fg-muted"}`
      }
    >
      {link.label}
    </Link>
  );
}
