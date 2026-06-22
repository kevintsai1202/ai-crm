/**
 * 將文字內容以 .md 格式下載至本機。
 * 函式級註解：使用 Blob + URL.createObjectURL 動態產生下載連結，不需後端支援。
 *
 * @param filename 檔名（不含副檔名亦可，會自動補 .md）
 * @param content Markdown 文字內容
 */
export function downloadMarkdown(filename: string, content: string) {
  const safeName = filename.replace(/[/\\:*?"<>|]/g, "_");
  const fullName = safeName.endsWith(".md") ? safeName : `${safeName}.md`;
  const blob = new Blob([content], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fullName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
