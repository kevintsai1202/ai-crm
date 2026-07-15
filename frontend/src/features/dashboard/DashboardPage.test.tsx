import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import i18n from "../../i18n";
import { DashboardPage } from "./DashboardPage";

// jsdom 未內建 ResizeObserver，但 GridLayout（react-grid-layout 的 WidthProvider）掛載時會用到，
// 故於此提供最小 stub，僅供本檔測試環境使用，不影響實際瀏覽器行為。
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
(globalThis as unknown as { ResizeObserver: typeof ResizeObserverStub }).ResizeObserver = ResizeObserverStub;

// 隔離 auth：僅需 SALES 角色即可渲染主要區塊（不觸發 usage 區塊的 MANAGER/ADMIN 限定請求）
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: 1, displayName: "Sales", role: "SALES" } })
}));

// 隔離所有 API 呼叫，回傳最小可渲染資料，避免測試依賴真實後端
vi.mock("../../api", () => ({
  fetchDashboard: vi.fn().mockResolvedValue({ customerCount: 3, activeOpportunityCount: 2, opportunityAmount: 100000, highRiskCount: 1 }),
  fetchDashboardReports: vi.fn().mockResolvedValue({
    pipelineByStage: [], monthlyForecast: [], industryBreakdown: [], riskBreakdown: [],
    renewalForecast: [], ownerLeaderboard: [], recentActivities: []
  }),
  fetchDashboardLayout: vi.fn().mockResolvedValue([]),
  fetchRfm: vi.fn().mockResolvedValue([]),
  fetchSentimentRadar: vi.fn().mockResolvedValue({
    intentDistribution: [], sentimentTrend: [], highRiskInteractions: [], churnRadar: [], priorityCare: []
  }),
  fetchAiUsage: vi.fn().mockResolvedValue({ totalCalls: 0, totalTokens: 0, realCalls: 0, fallbackCalls: 0, adopted: 0, rejected: 0 }),
  fetchPortfolioCalls: vi.fn().mockResolvedValue([]),
  saveDashboardLayout: vi.fn().mockResolvedValue(undefined),
  generateDemoData: vi.fn().mockResolvedValue(undefined),
  fetchDrilldown: vi.fn().mockResolvedValue(null),
  streamPortfolioAssessment: vi.fn()
}));

describe("DashboardPage i18n", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("預設英文顯示頁面標題與 KPI 標籤", async () => {
    await i18n.changeLanguage("en");
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Customers")).toBeInTheDocument();
    });
    expect(screen.getByText("Active opportunities")).toBeInTheDocument();
    expect(screen.getByText("Pipeline amount")).toBeInTheDocument();
  });

  it("切換繁中顯示頁面標題與 KPI 標籤", async () => {
    await i18n.changeLanguage("zh-TW");
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "儀表板" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("客戶數")).toBeInTheDocument();
    });
    expect(screen.getByText("活躍商機")).toBeInTheDocument();
    expect(screen.getByText("高風險客戶")).toBeInTheDocument();
  });
});
