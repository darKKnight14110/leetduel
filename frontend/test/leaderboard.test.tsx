import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LeaderboardPage from "@/app/leaderboard/page";
import { ApiError, getLeaderboardTop, getPublicIdentities } from "@/lib/api";

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));
vi.mock("next/navigation", () => ({
  usePathname: () => "/leaderboard",
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/components/Logo", () => ({ Logo: () => <span>LeetDuel</span> }));
vi.mock("@phosphor-icons/react", () => ({
  ArrowClockwise: () => <span aria-hidden="true" />,
  List: () => <span aria-hidden="true" />,
  X: () => <span aria-hidden="true" />,
}));
vi.mock("@/lib/auth", () => ({ getAccessToken: vi.fn(() => null) }));
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getLeaderboardTop: vi.fn(),
    getPublicIdentities: vi.fn(),
  };
});

describe("LeaderboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getPublicIdentities).mockResolvedValue([]);
  });

  it("renders a useful empty state after a successful empty response", async () => {
    vi.mocked(getLeaderboardTop).mockResolvedValue({ board: "GLOBAL", entries: [] });

    render(<LeaderboardPage />);

    expect(screen.getByRole("heading", { name: "Leaderboard" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("No one has played a match yet.")).toBeInTheDocument());
  });

  it("renders usernames and keeps a short-id fallback when identity is missing", async () => {
    vi.mocked(getLeaderboardTop).mockResolvedValue({
      board: "GLOBAL",
      entries: [
        { userId: "11111111-1111-1111-1111-111111111111", score: 1812, rank: 1 },
        { userId: "22222222-2222-2222-2222-222222222222", score: 1799, rank: 2 },
      ],
    });
    vi.mocked(getPublicIdentities).mockResolvedValue([
      { userId: "11111111-1111-1111-1111-111111111111", username: "alice" },
    ]);

    render(<LeaderboardPage />);

    await waitFor(() => expect(screen.getByText("alice")).toBeInTheDocument());
    expect(screen.getByText("Player 22222222")).toBeInTheDocument();
  });

  it("shows the gateway error instead of leaving a blank page", async () => {
    vi.mocked(getLeaderboardTop).mockRejectedValue(new ApiError("Gateway offline"));

    render(<LeaderboardPage />);

    await waitFor(() => expect(screen.getByText("Gateway offline")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});
