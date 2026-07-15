import { describe, it, expect } from "vitest";
import { detectLanguage } from "./detect";

describe("detectLanguage", () => {
  // 已存 localStorage 選擇時，優先採用（且忽略瀏覽器語系）
  it.each([
    ["en", "zh-TW", "zh-TW"],
    ["zh-TW", "en", "en"],
  ])("stored=%s 覆蓋 browser=%s → %s", (browser, stored, expected) => {
    expect(detectLanguage(browser, stored)).toBe(expected);
  });

  // 無 stored（或 stored 非法）時，依瀏覽器語系判斷；zh* → zh-TW，其餘 → en
  it.each([
    ["zh-TW", null, "zh-TW"],
    ["zh-CN", null, "zh-TW"],
    ["zh-HK", null, "zh-TW"],
    ["en-US", null, "en"],
    ["fr", null, "en"],
    [undefined, null, "en"],
    ["zh-TW", "invalid", "zh-TW"],
  ])("browser=%s stored=%s → %s", (browser, stored, expected) => {
    expect(detectLanguage(browser as string | undefined, stored)).toBe(expected);
  });
});
