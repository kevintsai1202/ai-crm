import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import i18n from "../../i18n";
import { TeamAnalyticsPage } from "./TeamAnalyticsPage";

vi.mock("../../api", () => ({
  fetchManagerAnalytics: vi.fn().mockResolvedValue({
    team: { totalCustomers: 13, totalWonAmount: 120000, totalPipeline: 300000, totalHighRisk: 2, avgWinRate: 0.5, ownerCount: 1 },
    owners: [{
      ownerId: 1, ownerName: "Alex", customerCount: 13, highRiskCount: 2,
      pipelineAmount: 300000, activeOpportunityCount: 3, wonAmount: 120000, wonCount: 1,
      winRate: 0.5, avgDaysSinceInteraction: 4, avgSentimentScore: null,
      renewalsThisMonth: 0, renewalsThisQuarter: 1,
    }],
  }),
  fetchTeamInsight: vi.fn(), fetchOwnerInsight: vi.fn(), streamTeamInsight: vi.fn(), streamOwnerInsight: vi.fn(),
  fetchTeamInsightCalls: vi.fn(), fetchOwnerInsightCalls: vi.fn(),
}));

describe("TeamAnalyticsPage i18n", () => {
  beforeEach(() => localStorage.clear());

  it("英文模式顯示團隊 KPI 與績效表欄位", async () => {
    await i18n.changeLanguage("en");
    render(<TeamAnalyticsPage />);

    expect(await screen.findByRole("heading", { name: "Team Analytics" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Team closed revenue")).toBeInTheDocument());
    expect(screen.getByRole("region", { name: "Sales performance table" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Sales rep" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Coaching report/i })).toBeInTheDocument();
  });

  it("繁中模式顯示團隊 KPI 與績效表欄位", async () => {
    await i18n.changeLanguage("zh-TW");
    render(<TeamAnalyticsPage />);

    expect(await screen.findByRole("heading", { name: "業務分析" })).toBeInTheDocument();
    expect(screen.getByText("全團隊成交金額")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "業務" })).toBeInTheDocument();
  });
});
