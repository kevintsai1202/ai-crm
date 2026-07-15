import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import i18n from "../../i18n";
import { CustomersPage } from "./CustomersPage";

// 隔離 auth：SALES 角色即可渲染主要區塊
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: 1, displayName: "Sales", role: "SALES" } })
}));

// 隔離 AI 對話 hook，避免測試觸發真實聊天狀態
vi.mock("../ai-assistant/useAiChat", () => ({
  useAiChat: () => ({
    messages: [], chatSending: false, chatOpen: false, setChatOpen: vi.fn(),
    sendChat: vi.fn(), resetChat: vi.fn(), loadHistory: vi.fn().mockResolvedValue(undefined), historyLoading: false
  })
}));

// 隔離所有 API 呼叫，回傳最小可渲染資料
vi.mock("../../api", () => ({
  fetchCustomers: vi.fn().mockResolvedValue({ items: [{ id: 1, name: "Acme", industry: "Tech", ownerName: "Sales", riskLevel: "LOW" }], totalPages: 1, totalElements: 1 }),
  fetchCustomerOptions: vi.fn().mockResolvedValue({ industries: ["Tech"], owners: [{ id: 1, displayName: "Sales" }] }),
  fetchCustomerDetail: vi.fn().mockResolvedValue({
    customer: { id: 1, name: "Acme", email: "a@acme.com", phone: "0900000000", industry: "Tech", riskLevel: "LOW", opportunityAmount: 0, renewalDueDate: null, lastInteractionAt: null, status: "ACTIVE" },
    contacts: [], interactions: [], opportunities: []
  }),
  fetchAgentTrace: vi.fn().mockResolvedValue({ finalRecommendation: "", steps: [] }),
  addInteraction: vi.fn(), createContact: vi.fn(), createCustomer: vi.fn(), createOpportunity: vi.fn(),
  deleteContact: vi.fn(), deleteCustomer: vi.fn(), deleteInteraction: vi.fn(), deleteOpportunity: vi.fn(),
  fetchCustomerAiCalls: vi.fn().mockResolvedValue([]), fetchCustomerAssessmentStream: vi.fn(),
  updateContact: vi.fn(), updateCustomer: vi.fn(), updateInteraction: vi.fn(), updateOpportunity: vi.fn(), updateOpportunityStage: vi.fn()
}));

describe("CustomersPage i18n", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("預設英文顯示頁面標題與客戶列表標題", async () => {
    await i18n.changeLanguage("en");
    render(
      <MemoryRouter initialEntries={["/customers/1"]}>
        <Routes>
          <Route path="/customers/:id" element={<CustomersPage />} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "Customer Workbench" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Customer list")).toBeInTheDocument();
    });
  });

  it("切換繁中顯示頁面標題與客戶列表標題", async () => {
    await i18n.changeLanguage("zh-TW");
    render(
      <MemoryRouter initialEntries={["/customers/1"]}>
        <Routes>
          <Route path="/customers/:id" element={<CustomersPage />} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: "客戶工作台" })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("客戶列表")).toBeInTheDocument();
    });
  });
});
