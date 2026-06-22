import { useEffect, useState } from "react";

/** 從 DOM script 標籤中取出 Vite 打包的 bundle hash（如 BjZJrFmd）。 */
function getCurrentBuildHash(): string {
  const scripts = document.querySelectorAll<HTMLScriptElement>("script[src]");
  for (const s of Array.from(scripts)) {
    const m = s.src.match(/\/assets\/index-([^.]+)\.js/);
    if (m) return m[1];
  }
  return "";
}

/** 拉最新 index.html（no-cache）並解析出 bundle hash。 */
async function fetchLatestBuildHash(): Promise<string> {
  try {
    const res = await fetch("/", { cache: "no-store", headers: { "Cache-Control": "no-cache" } });
    if (!res.ok) return "";
    const html = await res.text();
    const m = html.match(/\/assets\/index-([^.]+)\.js/);
    return m ? m[1] : "";
  } catch {
    return "";
  }
}

/** 每 5 分鐘（300s）比對一次 bundle hash，有新版本時回傳 true。 */
const POLL_INTERVAL_MS = 5 * 60 * 1000;

/**
 * 版本更新偵測 hook：比對當前 bundle hash 與最新部署版本。
 * 函式級註解：依賴 Vite content-hash（生產環境自動變更），開發環境 hash 為空字串不觸發通知。
 */
export function useVersionCheck(): boolean {
  const [hasUpdate, setHasUpdate] = useState(false);

  useEffect(() => {
    const currentHash = getCurrentBuildHash();
    // 開發環境無 hash，不啟動偵測
    if (!currentHash) return;

    const check = async () => {
      const latestHash = await fetchLatestBuildHash();
      if (latestHash && latestHash !== currentHash) {
        setHasUpdate(true);
      }
    };

    // 首次 30s 後才 check（給頁面穩定時間），之後每 5 分鐘一次
    const firstTimer = setTimeout(check, 30_000);
    const interval = setInterval(check, POLL_INTERVAL_MS);

    return () => {
      clearTimeout(firstTimer);
      clearInterval(interval);
    };
  }, []);

  return hasUpdate;
}
