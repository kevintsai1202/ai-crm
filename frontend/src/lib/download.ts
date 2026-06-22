import { zipSync, strToU8 } from "fflate";

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

/**
 * 將多個文字檔打包成 ZIP 並下載。
 * 函式級註解：使用 fflate.zipSync 在瀏覽器端產生 ZIP 二進位，不需後端支援。
 *
 * @param zipFilename ZIP 檔名（不含副檔名）
 * @param files 鍵為資料夾內的檔名，值為檔案文字內容
 */
export function downloadZip(zipFilename: string, files: Record<string, string>) {
  // 將所有文字檔轉成 Uint8Array，組成 fflate 所需格式
  const entries: Record<string, Uint8Array> = {};
  for (const [name, content] of Object.entries(files)) {
    const safeName = name.replace(/[*?"<>|]/g, "_");
    entries[safeName] = strToU8(content);
  }
  const zipped = zipSync(entries);
  const blob = new Blob([zipped], { type: "application/zip" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${zipFilename.replace(/[/\\:*?"<>|]/g, "_")}.zip`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
