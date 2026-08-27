import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProblemDetailPage from "@/app/problems/[id]/page";
import { ApiError, createSubmission, getProblem } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

const push = vi.fn();
const router = { push };

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "problem-1" }),
  useRouter: () => router,
}));
vi.mock("@/components/layout/AppShell", () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock("@/components/editor/CodeEditor", () => ({
  CodeEditor: ({ value, language, disabled, onChange }: {
    value: string;
    language: string;
    disabled?: boolean;
    onChange: (value: string) => void;
  }) => (
    <textarea
      aria-label={`${language} code editor`}
      value={value}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}));
vi.mock("@/components/problems/DifficultyBadge", () => ({
  DifficultyBadge: ({ difficulty }: { difficulty: string }) => <span>{difficulty}</span>,
}));
vi.mock("@phosphor-icons/react", () => ({
  ArrowClockwise: () => <span aria-hidden="true" />,
}));
vi.mock("@/lib/auth", () => ({ getAccessToken: vi.fn() }));
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getProblem: vi.fn(),
    createSubmission: vi.fn(),
    getSubmission: vi.fn(),
  };
});

const problem = {
  id: "problem-1",
  slug: "two-sum",
  title: "Two Sum",
  description: "Return the indices of the two numbers.",
  difficulty: "EASY" as const,
  functionName: "twoSum",
  returnType: "int[]",
  parameters: [],
  languageStubs: {
    PYTHON: "def two_sum(nums, target):\n    pass",
    JAVA: "class Solution {\n    int[] twoSum() {}\n}",
  },
  sampleTestCases: [],
};

const pendingSubmission = {
  id: "submission-1",
  problemId: "problem-1",
  language: "PYTHON" as const,
  sourceCode: problem.languageStubs.PYTHON,
  status: "PENDING" as const,
  verdict: null,
  testCasesPassed: null,
  testCasesTotal: null,
  testResults: null,
  createdAt: "2026-08-28T00:00:00Z",
  judgedAt: null,
};

describe("ProblemDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAccessToken).mockReturnValue("test-token");
  });

  it("shows loading and then the loaded challenge", async () => {
    vi.mocked(getProblem).mockResolvedValue(problem);

    render(<ProblemDetailPage />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading problem...");
    expect(await screen.findByRole("heading", { name: "Two Sum" })).toBeInTheDocument();
  });

  it("keeps independent drafts when switching languages", async () => {
    vi.mocked(getProblem).mockResolvedValue(problem);
    const user = userEvent.setup();
    render(<ProblemDetailPage />);

    const pythonEditor = await screen.findByRole("textbox", { name: "PYTHON code editor" });
    fireEvent.change(pythonEditor, { target: { value: `${problem.languageStubs.PYTHON}# keep this draft` } });
    await user.click(screen.getByRole("tab", { name: "Java" }));
    expect(screen.getByRole("textbox", { name: "JAVA code editor" })).toHaveValue(problem.languageStubs.JAVA);
    await user.click(screen.getByRole("tab", { name: "Python" }));
    expect(screen.getByRole("textbox", { name: "PYTHON code editor" })).toHaveValue(`${problem.languageStubs.PYTHON}# keep this draft`);
  });

  it("disables submission and shows judging state while the backend evaluates code", async () => {
    vi.mocked(getProblem).mockResolvedValue(problem);
    vi.mocked(createSubmission).mockResolvedValue(pendingSubmission);
    const user = userEvent.setup();
    render(<ProblemDetailPage />);

    const submit = await screen.findByRole("button", { name: "Check solution" });
    await user.click(submit);

    await waitFor(() => expect(screen.getByText("Judging...")).toBeInTheDocument());
    expect(submit).toBeDisabled();
  });

  it("offers retry after a load failure", async () => {
    vi.mocked(getProblem)
      .mockRejectedValueOnce(new ApiError("Problem service unavailable"))
      .mockResolvedValueOnce(problem);
    const user = userEvent.setup();
    render(<ProblemDetailPage />);

    expect(await screen.findByText("Problem service unavailable")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("heading", { name: "Two Sum" })).toBeInTheDocument();
  });

  it("redirects unauthenticated visitors", async () => {
    vi.mocked(getAccessToken).mockReturnValue(null);

    render(<ProblemDetailPage />);

    await waitFor(() => expect(push).toHaveBeenCalledWith("/login"));
    expect(getProblem).not.toHaveBeenCalled();
  });
});
