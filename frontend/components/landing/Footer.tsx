import { Logo } from "@/components/Logo";

export function Footer() {
  return (
    <footer className="mx-auto w-full max-w-7xl px-6 py-10">
      <div className="flex flex-col items-center justify-between gap-4 border-t border-border pt-8 md:flex-row">
        <Logo />
        <p className="text-sm text-fg-muted">© {new Date().getFullYear()} LeetDuel</p>
      </div>
    </footer>
  );
}
