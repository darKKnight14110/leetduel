import Link from "next/link";
import { Logo } from "@/components/Logo";
import { AuthCard } from "@/components/auth/AuthCard";

export const metadata = {
  title: "Log in - LeetDuel",
};

export default function LoginPage() {
  return (
    <main className="flex flex-1 flex-col">
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-accent px-4 py-2 font-medium text-accent-ink focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <div className="px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
      </div>
      <div id="main-content" className="flex flex-1 items-center justify-center px-6 py-16">
        <AuthCard />
      </div>
    </main>
  );
}
