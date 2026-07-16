import { fireEvent, render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AppShell } from "./AppShell";
import i18n from "../i18n";

// 隔離 auth 與版本輪詢，避免測試觸發網路
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({
    user: { displayName: "Sales", role: "SALES" },
    health: null,
    healthError: false,
    refreshHealth: vi.fn(),
    logout: vi.fn()
  })
}));
vi.mock("../hooks/useVersionCheck", () => ({
  useVersionCheck: () => false
}));

describe("AppShell", () => {
  // 確保 i18next 完成初始化後才渲染，避免 NO_I18NEXT_INSTANCE 警告（比照同層測試作法）
  beforeEach(async () => {
    localStorage.clear();
    await i18n.changeLanguage("en");
  });

  it("側邊欄含語言切換下拉", () => {
    render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>
    );
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("可收合側邊欄並保存使用者偏好", () => {
    const { container } = render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>
    );

    const toggle = screen.getByRole("button", { name: "Collapse menu" });
    fireEvent.click(toggle);

    expect(container.querySelector(".app-shell")).toHaveClass("sidebar-collapsed");
    expect(screen.getByRole("button", { name: "Expand menu" })).toHaveAttribute("aria-expanded", "false");
    expect(localStorage.getItem("ai-crm-sidebar-collapsed")).toBe("true");
  });

  it("預設英文顯示側邊欄導覽與登出按鈕", async () => {
    await i18n.changeLanguage("en");
    render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>
    );
    expect(screen.getByRole("link", { name: /Dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Customers/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log out" })).toBeInTheDocument();
  });

  it("切換繁中顯示側邊欄導覽與登出按鈕", async () => {
    await i18n.changeLanguage("zh-TW");
    render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>
    );
    expect(screen.getByRole("link", { name: /儀表板/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /客戶工作台/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登出" })).toBeInTheDocument();
  });
});
