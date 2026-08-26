"use client";

import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import { CheckCircle, ArrowClockwise } from "@phosphor-icons/react";
import { Button } from "@/components/ui/Button";
import { signup, login, decodeJwtPayload, ApiError, type AuthResult } from "@/lib/api";

type Mode = "login" | "signup";

// Real signup + login against auth-service - the one thing on this whole
// site with a working backend, per the task brief. No Google button here:
// Google Sign-In was removed from auth-service entirely (email/password +
// email verification only).
export function AuthCard() {
  const [mode, setMode] = useState<Mode>("login");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AuthResult | null>(null);

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const auth = mode === "signup" ? await signup(username, email, password) : await login(identifier, password);
      setResult(auth);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Is auth-service running?");
    } finally {
      setLoading(false);
    }
  }

  if (result) {
    return <LoggedInPanel result={result} onLogout={() => setResult(null)} />;
  }

  return (
    <div className="w-full max-w-sm rounded-2xl border border-border bg-surface p-8">
      <div className="flex gap-1 rounded-full border border-border bg-surface-2 p-1">
        <TabButton active={mode === "login"} onClick={() => setMode("login")}>
          Log in
        </TabButton>
        <TabButton active={mode === "signup"} onClick={() => setMode("signup")}>
          Sign up
        </TabButton>
      </div>

      <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
        {mode === "signup" && (
          <Field label="Username">
            <input
              required
              minLength={3}
              maxLength={30}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className={inputClass}
              placeholder="rkumar"
            />
          </Field>
        )}

        {mode === "signup" ? (
          <Field label="Email">
            <input
              required
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClass}
              placeholder="you@example.com"
            />
          </Field>
        ) : (
          <Field label="Username or email">
            <input
              required
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              className={inputClass}
              placeholder="rkumar or you@example.com"
            />
          </Field>
        )}

        <Field label="Password">
          <input
            required
            type="password"
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
            placeholder="At least 8 characters"
          />
        </Field>

        {error && <p className="text-sm text-danger">{error}</p>}

        <Button type="submit" variant="primary" disabled={loading} className="mt-2 w-full">
          {loading ? (
            <ArrowClockwise className="animate-spin" size={18} weight="bold" />
          ) : mode === "signup" ? (
            "Create account"
          ) : (
            "Log in"
          )}
        </Button>
      </form>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 rounded-full py-2 text-sm font-medium transition-colors ${
        active ? "bg-accent text-accent-ink" : "text-fg-muted hover:text-fg"
      }`}
    >
      {children}
    </button>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-2 text-sm">
      <span className="text-fg-muted">{label}</span>
      {children}
    </label>
  );
}

const inputClass =
  "rounded-lg border border-border-strong bg-surface-2 px-4 py-2.5 text-fg placeholder:text-fg-muted/60 outline-none focus:border-accent focus:ring-1 focus:ring-accent";

function LoggedInPanel({ result, onLogout }: { result: AuthResult; onLogout: () => void }) {
  const payload = decodeJwtPayload(result.accessToken);
  const subject = typeof payload?.sub === "string" ? payload.sub : "unknown";
  const emailVerified = payload?.emailVerified === true;

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className="w-full max-w-sm rounded-2xl border border-border bg-surface p-8 text-center"
    >
      <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-success/15">
        <CheckCircle size={28} weight="fill" className="text-success" />
      </div>
      <h2 className="mt-4 text-xl font-medium text-fg">You&apos;re logged in</h2>
      <p className="mt-1 font-mono text-xs text-fg-muted break-all">{subject}</p>

      <div className="mt-4 flex justify-center">
        <span
          className={`rounded-full px-3 py-1 font-mono text-xs ${
            emailVerified ? "bg-success/15 text-success" : "bg-surface-2 text-fg-muted"
          }`}
        >
          {emailVerified ? "Email verified" : "Email not verified yet"}
        </span>
      </div>

      <p className="mt-6 text-sm text-fg-muted">
        Access and refresh tokens were issued by auth-service and are held in memory for this demo.
      </p>

      <Button variant="ghost" onClick={onLogout} className="mt-6 w-full">
        Log out
      </Button>
    </motion.div>
  );
}
