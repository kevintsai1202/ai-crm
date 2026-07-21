import ts from "typescript";
import { describe, expect, it } from "vitest";

const HAN_PATTERN = /\p{Script=Han}/u;
/** 由 Vite 以 raw string 載入正式 TSX；不使用 Node fs，確保 production typecheck 也可通過。 */
const COMPONENT_SOURCES = import.meta.glob("../**/*.tsx", {
  eager: true,
  query: "?raw",
  import: "default",
}) as Record<string, string>;

/** 收集 JSX 直接顯示及 UI 錯誤 setter 中尚未移入 i18n 的中文。 */
function findHardcodedUiText(file: string, content: string): string[] {
  const source = ts.createSourceFile(file, content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const findings: string[] = [];

  /** 記錄節點所在行與文字，讓失敗訊息可直接定位。 */
  function record(node: ts.Node, value: string) {
    if (!HAN_PATTERN.test(value)) return;
    const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
    findings.push(`${file}:${line} ${JSON.stringify(value)}`);
  }

  /** 檢查 JSX expression；事件 callback 內會由其巢狀 JSX 或 UI setter 各自檢查。 */
  function inspectRenderedExpression(node: ts.Node) {
    if (ts.isFunctionLike(node)) return;
    if (ts.isStringLiteralLike(node) || ts.isNoSubstitutionTemplateLiteral(node)) record(node, node.text);
    ts.forEachChild(node, inspectRenderedExpression);
  }

  function visit(node: ts.Node) {
    if (ts.isJsxText(node)) record(node, node.text.trim());
    if (ts.isJsxAttribute(node) && node.initializer && ts.isStringLiteral(node.initializer)) record(node.initializer, node.initializer.text);
    if (ts.isJsxExpression(node) && node.expression) inspectRenderedExpression(node.expression);

    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
      const name = node.expression.text;
      const isUiMessageSetter = /^set.*(?:Error|Message|Msg)$/.test(name) || name === "alert" || name === "confirm";
      if (isUiMessageSetter) {
        node.arguments.forEach((argument) => {
          if (ts.isStringLiteralLike(argument) || ts.isNoSubstitutionTemplateLiteral(argument)) record(argument, argument.text);
        });
      }
    }
    ts.forEachChild(node, visit);
  }

  visit(source);
  return findings;
}

describe("i18n UI coverage", () => {
  it("正式 TSX 介面不可直接寫死中文", () => {
    const findings = Object.entries(COMPONENT_SOURCES)
      .filter(([file]) => !file.endsWith(".test.tsx"))
      .flatMap(([file, content]) => findHardcodedUiText(file, content));
    expect(findings, findings.join("\n")).toEqual([]);
  });
});
