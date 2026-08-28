import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProblemsPage from "@/app/problems/page";
import { getPracticeOverview, listProblems } from "@/lib/api";

const push = vi.fn();
const router = { push };

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => <a href={href} {...props}>{children}</a>,
}));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/components/layout/AppShell", () => ({ AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("@/components/ui/PageState", () => ({ LoadingState: ({ label }: { label: string }) => <div role="status">{label}</div>, ErrorState: ({ message }: { message: string }) => <div role="alert">{message}</div> }));
vi.mock("@/components/problems/DifficultyBadge", () => ({ DifficultyBadge: ({ difficulty }: { difficulty: string }) => <span>{difficulty}</span> }));
vi.mock("@phosphor-icons/react", () => ({ CheckCircle: (props: React.HTMLAttributes<HTMLSpanElement>) => <span {...props} />, Compass: () => <span />, Funnel: () => <span />, Sparkle: () => <span /> }));
vi.mock("@/lib/auth", () => ({ getAccessToken: vi.fn(() => "token") }));
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, listProblems: vi.fn(), getPracticeOverview: vi.fn() };
});

describe("ProblemsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listProblems).mockResolvedValue({ content: [
      { id: "1", slug: "two-sum", title: "Two Sum", difficulty: "EASY" },
      { id: "2", slug: "binary-search", title: "Binary Search", difficulty: "MEDIUM" },
    ], totalPages: 1, number: 0 });
    vi.mocked(getPracticeOverview).mockResolvedValue({ attemptedCount: 2, solvedCount: 1, solvedProblemIds: ["1"], attemptedProblemIds: ["1", "2"], recommendations: [{ problemId: "2", slug: "binary-search", title: "Binary Search", difficulty: "MEDIUM", tags: ["binary-search"], reason: "Practice your weak topic: binary-search", score: 0.8 }] });
  });

  it("shows progress, recommendations, and solved catalog state", async () => {
    render(<ProblemsPage />);

    expect(await screen.findByText("Recommended for you")).toBeInTheDocument();
    expect(screen.getByText("Problems solved")).toBeInTheDocument();
    expect(screen.getByLabelText("Solved")).toBeInTheDocument();
  });

  it("filters the catalog without another network request", async () => {
    const user = userEvent.setup();
    render(<ProblemsPage />);
    await screen.findByText("Problem catalog");
    const callsBeforeFilter = vi.mocked(listProblems).mock.calls.length;

    await user.selectOptions(screen.getByLabelText("Filter by difficulty"), "MEDIUM");

    await waitFor(() => expect(screen.queryByText("Two Sum")).not.toBeInTheDocument());
    expect(screen.getAllByText("Binary Search").length).toBeGreaterThan(0);
    expect(listProblems).toHaveBeenCalledTimes(callsBeforeFilter);
  });
});
