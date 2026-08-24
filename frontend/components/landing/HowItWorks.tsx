"use client";

import { motion, useReducedMotion } from "motion/react";

// Asymmetric bento (skill Section 4.7 - Bento Cell Count Rule: 3 items, 3
// cells, no filler tile). First cell spans full width so the grid isn't a
// row of 3 identical equal-width cards (the banned default pattern in
// Section 9.C).
const steps = [
  {
    title: "Queue up",
    body: "Tell us your rating and we find someone in your range. Matches typically form in under a minute.",
    wide: true,
  },
  {
    title: "Solve live",
    body: "Both players get the same problem and the same clock. Progress updates in real time, no code leaks.",
  },
  {
    title: "Climb the ladder",
    body: "Win and your rating climbs. Lose and you're matched softer next time. Simple ELO, no gimmicks.",
  },
];

export function HowItWorks() {
  const reduce = useReducedMotion();

  return (
    <section id="how-it-works" className="mx-auto max-w-7xl px-6 py-24 md:py-32">
      <h2 className="max-w-xl text-3xl font-semibold tracking-tight text-fg md:text-4xl">
        Three steps from queue to rating change.
      </h2>

      <div className="mt-10 grid grid-cols-1 gap-4 md:grid-cols-2">
        {steps.map((step, i) => (
          <motion.div
            key={step.title}
            initial={reduce ? false : { opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.5, delay: i * 0.08, ease: [0.16, 1, 0.3, 1] }}
            className={`rounded-2xl border border-border bg-surface p-8 ${
              step.wide ? "md:col-span-2" : ""
            }`}
          >
            <h3 className="text-xl font-medium text-fg">{step.title}</h3>
            <p className="mt-3 max-w-[52ch] text-fg-muted leading-relaxed">{step.body}</p>
          </motion.div>
        ))}
      </div>
    </section>
  );
}
