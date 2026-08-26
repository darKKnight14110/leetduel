import type { Difficulty } from "@/lib/api";

// Reuses the exact --difficulty-easy/--difficulty-hard tokens from
// globals.css that DifficultyBento already established on the landing
// page (Section 4.2's Color Consistency Lock) - Medium reuses --accent
// there too, same reasoning: it isn't a second color in the palette.
const colors: Record<Difficulty, string> = {
  EASY: "var(--difficulty-easy)",
  MEDIUM: "var(--accent)",
  HARD: "var(--difficulty-hard)",
};

export function DifficultyBadge({ difficulty }: { difficulty: Difficulty }) {
  return (
    <span
      className="rounded-full px-3 py-1 text-xs font-medium capitalize"
      style={{ color: colors[difficulty], backgroundColor: `${colors[difficulty]}26` }}
    >
      {difficulty.toLowerCase()}
    </span>
  );
}
