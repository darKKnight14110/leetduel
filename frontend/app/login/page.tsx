import Link from "next/link";
import { Logo } from "@/components/Logo";
import { AuthCard } from "@/components/auth/AuthCard";

export const metadata = {
  title: "Log in - LeetDuel",
};

export default function LoginPage() {
  return (
    <main className="flex flex-1 flex-col">
      <div className="px-6 pt-8">
        <Link href="/" aria-label="LeetDuel home">
          <Logo />
        </Link>
      </div>
      <div className="flex flex-1 items-center justify-center px-6 py-16">
        <AuthCard />
      </div>
    </main>
  );
}
