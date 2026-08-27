import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthCard } from "@/components/auth/AuthCard";
import { ApiError, login } from "@/lib/api";

vi.mock("@/components/ui/Button", () => ({
  Button: ({ children, ...props }: { children: React.ReactNode; type?: "button" | "submit"; disabled?: boolean; onClick?: () => void }) => (
    <button {...props}>{children}</button>
  ),
}));
vi.mock("@phosphor-icons/react", () => ({
  ArrowClockwise: () => <span aria-hidden="true" />,
  CheckCircle: () => <span aria-hidden="true" />,
}));
vi.mock("motion/react", () => ({
  motion: { div: ({ children, ...props }: { children: React.ReactNode }) => <div {...props}>{children}</div> },
}));
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, login: vi.fn() };
});

describe("AuthCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
  });

  it("shows a retryable authentication error", async () => {
    vi.mocked(login).mockRejectedValue(new ApiError("Invalid credentials"));
    const user = userEvent.setup();

    render(<AuthCard />);
    await user.type(screen.getByRole("textbox", { name: "Username or email" }), "alice");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getAllByRole("button", { name: "Log in" })[1]);

    expect(await screen.findByText("Invalid credentials")).toBeInTheDocument();
  });

  it("persists a successful login and exposes the next action", async () => {
    const token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhYmMxMjMifQ.signature";
    vi.mocked(login).mockResolvedValue({ accessToken: token, refreshToken: "refresh" });
    const user = userEvent.setup();

    render(<AuthCard />);
    await user.type(screen.getByRole("textbox", { name: "Username or email" }), "alice");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getAllByRole("button", { name: "Log in" })[1]);

    expect(await screen.findByText("Your account is ready")).toBeInTheDocument();
    expect(window.localStorage.getItem("leetduel.accessToken")).toBe(token);
    expect(screen.getByRole("button", { name: "Browse problems" })).toBeInTheDocument();
  });
});
