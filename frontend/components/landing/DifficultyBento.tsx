// Colored bento strip - satisfies the skill's Bento Background Diversity
// rule (not all-white-on-white cards) using LeetCode's own difficulty-color
// convention rather than an invented palette. Medium reuses the brand
// accent instead of introducing a second yellow.
const tiers = [
  {
    label: "Easy",
    title: "Warm up",
    body: "Quick wins to get your fingers moving before a match.",
    color: "var(--difficulty-easy)",
  },
  {
    label: "Medium",
    title: "The default",
    body: "Most ranked matches land here. Fair competition, real pressure.",
    color: "var(--accent)",
  },
  {
    label: "Hard",
    title: "Prove it",
    body: "For players chasing the top of the ladder.",
    color: "var(--difficulty-hard)",
  },
];

export function DifficultyBento() {
  return (
    <section id="ratings" className="mx-auto max-w-7xl px-6 py-24 md:py-32">
      <h2 className="max-w-xl text-3xl font-semibold tracking-tight text-fg md:text-4xl">
        Every rating tier, matched fairly.
      </h2>

      <div className="mt-10 grid grid-cols-1 gap-4 md:grid-cols-3">
        {tiers.map((tier) => (
          <div
            key={tier.label}
            className="rounded-2xl border border-border p-8"
            style={{ backgroundColor: `color-mix(in srgb, ${tier.color} 12%, var(--surface))` }}
          >
            <span
              className="font-mono text-xs font-medium"
              style={{ color: tier.color }}
            >
              {tier.label}
            </span>
            <h3 className="mt-3 text-xl font-medium text-fg">{tier.title}</h3>
            <p className="mt-2 text-fg-muted leading-relaxed">{tier.body}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
