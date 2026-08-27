"use client";

import { motion, useReducedMotion } from "motion/react";
import { Button } from "@/components/ui/Button";

// Asymmetric split hero (design-taste-frontend skill Section 10 / Anti-
// Center-Bias in 4.3) - copy on the left, an illustrative "duel card"
// graphic on the right. The card is a stylized graphic, not a fake
// screenshot of the product UI (see skill 9.E on div-based fake
// screenshots) - it never claims to be a live view of the app.
export function Hero() {
  const reduce = useReducedMotion();

  return (
    <section className="mx-auto max-w-7xl px-6 pt-20 pb-20 md:pt-24 md:pb-28">
      <div className="grid w-full grid-cols-1 items-center gap-12 md:grid-cols-12 md:gap-8">
        <motion.div
          initial={reduce ? false : { opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
          className="md:col-span-7"
        >
          <h1 className="text-5xl font-semibold tracking-tighter leading-[0.95] text-fg md:text-6xl lg:text-7xl">
            Practice with purpose.
            <br />
            Compete in real time.
          </h1>
          <p className="mt-6 max-w-[46ch] text-lg leading-relaxed text-fg-muted">
            Solve the same coding challenge as another developer, race the
            clock, and build an ELO rating from every result.
          </p>
          <div className="mt-8 flex items-center gap-4">
            <Button href="/login" variant="primary" className="px-8 py-3.5 text-base">
              Find a match
            </Button>
            <Button href="#how-it-works" variant="ghost" className="px-8 py-3.5 text-base">
              See how it works
            </Button>
          </div>
        </motion.div>

        <motion.div
          initial={reduce ? false : { opacity: 0, scale: 0.96 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
          className="md:col-span-5"
        >
          <DuelCard />
        </motion.div>
      </div>
    </section>
  );
}

function DuelCard() {
  return (
    <div className="rounded-2xl border border-border bg-surface p-6 shadow-[0_24px_80px_-24px_rgba(0,0,0,0.6)]">
      <div className="flex items-center justify-between text-xs">
        <span className="rounded-full bg-accent/15 px-3 py-1 font-mono text-accent">
          Medium
        </span>
        <span className="font-mono text-fg-muted">04:12 left</span>
      </div>

      <div className="mt-6 grid grid-cols-[1fr_auto_1fr] items-center gap-4">
        <Player initials="RK" name="rkumar" rating={1842} progress={70} />
        <span className="font-mono text-sm text-fg-muted">vs</span>
        <Player initials="MS" name="msato" rating={1798} progress={45} align="right" />
      </div>

      <div className="mt-6 rounded-xl border border-border bg-surface-2 p-4">
        <p className="font-mono text-sm text-fg-muted">Longest Increasing Subsequence</p>
        <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-surface">
          <div className="h-full w-[70%] rounded-full bg-accent" />
        </div>
      </div>
    </div>
  );
}

function Player({
  initials,
  name,
  rating,
  progress,
  align = "left",
}: {
  initials: string;
  name: string;
  rating: number;
  progress: number;
  align?: "left" | "right";
}) {
  return (
    <div className={`flex flex-col gap-2 ${align === "right" ? "items-end text-right" : "items-start"}`}>
      <div className="flex items-center gap-2">
        {align === "left" && <Avatar initials={initials} />}
        <div>
          <p className="text-sm font-medium text-fg">{name}</p>
          <p className="font-mono text-xs text-fg-muted">{rating} ELO</p>
        </div>
        {align === "right" && <Avatar initials={initials} />}
      </div>
      <span className="font-mono text-xs text-accent">{progress}%</span>
    </div>
  );
}

function Avatar({ initials }: { initials: string }) {
  return (
    <div className="flex h-9 w-9 items-center justify-center rounded-full bg-surface-2 text-xs font-medium text-fg">
      {initials}
    </div>
  );
}
