import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// 每個測試結束後卸載渲染結果，避免 DOM 殘留互相污染
afterEach(() => {
  cleanup();
});
