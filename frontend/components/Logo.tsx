// Hand-drawn mark, not a placeholder text wordmark - a single simple
// geometric glyph (two triangles facing off, "duel"), per the
// design-taste-frontend skill's allowance for a simple geometric mark when
// no real brand asset exists yet.
export function Logo({ className = "" }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <svg
        width="26"
        height="26"
        viewBox="0 0 26 26"
        fill="none"
        aria-hidden="true"
      >
        <rect width="26" height="26" rx="7" fill="var(--accent)" />
        <path d="M8 7L13 13L8 19" stroke="#16130A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M18 7L13 13L18 19" stroke="#16130A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="font-semibold tracking-tight text-fg">LeetDuel</span>
    </span>
  );
}
