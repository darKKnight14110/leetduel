import Link from "next/link";
import type { ReactNode } from "react";

type ButtonProps = {
  children: ReactNode;
  href?: string;
  type?: "button" | "submit";
  variant?: "primary" | "ghost";
  disabled?: boolean;
  className?: string;
  onClick?: () => void;
};

// Single shared button so every CTA on the site (nav, hero, CTA band, auth
// form) shares the exact same shape and label discipline - see the
// design-taste-frontend skill's Shape Consistency Lock and No Duplicate CTA
// Intent rules. Full-pill radius for every interactive control, per the
// corner-radius scale locked in globals.css.
const base =
  "inline-flex items-center justify-center whitespace-nowrap rounded-full px-6 py-3 text-sm font-medium transition-transform hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none";

const variants = {
  primary: "bg-accent text-accent-ink hover:bg-accent-strong",
  ghost:
    "border border-border-strong text-fg hover:bg-surface hover:border-fg-muted",
};

export function Button({
  children,
  href,
  type = "button",
  variant = "primary",
  disabled,
  className = "",
  onClick,
}: ButtonProps) {
  const classes = `${base} ${variants[variant]} ${className}`;

  if (href) {
    return (
      <Link href={href} className={classes}>
        {children}
      </Link>
    );
  }

  return (
    <button type={type} disabled={disabled} onClick={onClick} className={classes}>
      {children}
    </button>
  );
}
