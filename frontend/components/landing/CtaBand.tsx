import { Button } from "@/components/ui/Button";

export function CtaBand() {
  return (
    <section className="border-y border-border bg-surface">
      <div className="mx-auto max-w-7xl px-6 py-20 text-center">
        <h2 className="text-3xl font-semibold tracking-tight text-fg md:text-4xl">
          Your next challenge is ready when you are.
        </h2>
        <div className="mt-8">
          <Button href="/login" variant="primary" className="px-8 py-3.5 text-base">
            Find a match
          </Button>
        </div>
      </div>
    </section>
  );
}
