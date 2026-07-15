import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AppShell } from "./AppShell";

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
  it("側邊欄含語言切換下拉", () => {
    render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>
    );
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });
});
