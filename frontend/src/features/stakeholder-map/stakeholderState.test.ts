import { describe, expect, it } from "vitest";
import { edgeStyle, isPending, nodeLabel } from "./stakeholderState";

describe("決策鏈圖呈現", () => {
  it("已確認用實線、待確認用虛線", () => {
    expect(edgeStyle("CONFIRMED")).toBe("solid");
    expect(edgeStyle("SUGGESTED")).toBe("dashed");
  });

  it("僅 SUGGESTED 視為待確認", () => {
    expect(isPending("SUGGESTED")).toBe(true);
    expect(isPending("CONFIRMED")).toBe(false);
    expect(isPending("REJECTED")).toBe(false);
  });

  it("節點標籤含姓名與職稱（無職稱時只顯示姓名）", () => {
    expect(nodeLabel("王經理", "採購經理")).toBe("王經理（採購經理）");
    expect(nodeLabel("李總", null)).toBe("李總");
  });
});
