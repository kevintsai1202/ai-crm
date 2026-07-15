import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, beforeEach } from "vitest";
import i18n from "../../i18n";
import { LanguageSwitcher } from "./LanguageSwitcher";

describe("LanguageSwitcher", () => {
  beforeEach(async () => {
    localStorage.clear();
    await i18n.changeLanguage("en");
  });

  it("顯示兩個語言選項", () => {
    render(<LanguageSwitcher />);
    expect(screen.getByRole("option", { name: "English" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "繁體中文" })).toBeInTheDocument();
  });

  it("切換語言會更新 i18n 並寫入 localStorage", () => {
    render(<LanguageSwitcher />);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "zh-TW" } });
    expect(i18n.resolvedLanguage).toBe("zh-TW");
    expect(localStorage.getItem("ai-crm-lang")).toBe("zh-TW");
  });
});
