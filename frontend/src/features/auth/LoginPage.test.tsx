import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeEach, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import i18n from "../../i18n";
import { LoginPage } from "./LoginPage";

// 隔離 AuthContext：試點測試只驗證 i18n 文字，不觸發真實登入/網路
vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ isAuthed: false, login: vi.fn() })
}));

describe("LoginPage i18n", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("預設英文顯示 Sign in 標題與按鈕", async () => {
    await i18n.changeLanguage("en");
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
    expect(screen.getByRole("heading", { name: /Sign in to AI CRM/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
  });

  it("切換繁中顯示登入按鈕", async () => {
    await i18n.changeLanguage("zh-TW");
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
    expect(screen.getByRole("button", { name: "登入" })).toBeInTheDocument();
  });
});
