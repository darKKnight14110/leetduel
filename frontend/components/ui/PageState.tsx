import { ArrowClockwise } from "@phosphor-icons/react";
import { Button } from "@/components/ui/Button";

export function LoadingState({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-1 items-center justify-center px-6">
      <div className="flex items-center gap-3 text-sm text-fg-muted">
        <ArrowClockwise className="animate-spin text-accent" size={20} weight="bold" />
        <span>{label}</span>
      </div>
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-1 items-center justify-center px-6">
      <div className="flex max-w-sm flex-col items-center gap-4 text-center">
        <p className="text-sm text-danger">{message}</p>
        {onRetry && <Button variant="ghost" onClick={onRetry}>Try again</Button>}
      </div>
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <p className="p-6 text-center text-sm text-fg-muted">{message}</p>;
}
