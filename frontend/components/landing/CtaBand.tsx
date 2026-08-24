import { Button } from "@/components/ui/Button";

// Full-width closing CTA - same label as the hero's primary CTA ("Start
// Dueling"), not a reworded duplicate, per the skill's No Duplicate CTA
// Intent rule.
export function CtaBand() {
  return (
    <section className="border-y border-border bg-surface">
      <div className="mx-auto max-w-7xl px-6 py-20 text-center">
        <h2 className="text-3xl font-semibold tracking-tight text-fg md:text-4xl">
          Your next match is one queue away.
        </h2>
        <div className="mt-8">
          <Button href="/login" variant="primary" className="px-8 py-3.5 text-base">
            Start Dueling
          </Button>
        </div>
      </div>
    </section>
  );
}
