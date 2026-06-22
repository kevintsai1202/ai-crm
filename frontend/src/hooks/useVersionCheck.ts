import { useEffect, useState } from "react";

/** 從 /build-info.json 讀取前端 buildTime（由 Vite buildInfoPlugin 在 build 時寫入）。 */
async function fetchFrontendBuildTime(bustCache = false): Promise<number> {
  try {
    const url = bustCache ? `/build-info.json?_t=${Date.now()}` : "/build-info.json";
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) return 0;
    const data = await res.json();
    return typeof data.buildTime === "number" ? data.buildTime : 0;
  } catch {
    return 0;
  }
}

/** 從 /api/health 讀取後端 serverStartTime（後端每次重新部署/重啟後值改變）。 */
async function fetchBackendStartTime(): Promise<number> {
  try {
    const res = await fetch(`/api/health?_t=${Date.now()}`, { cache: "no-store" });
    if (!res.ok) return 0;
    const data = await res.json();
    return typeof data.serverStartTime === "number" ? data.serverStartTime : 0;
  } catch {
    return 0;
  }
}

/** 每 5 分鐘輪詢一次；前端 build hash 或後端啟動時間任一改變即通知。 */
const POLL_MS = 5 * 60 * 1000;

/**
 * 版本更新偵測 hook：同時監聽前端 bundle buildTime 與後端 serverStartTime。
 * 函式級註解：
 * - 前端部署 → build-info.json 的 buildTime 改變 → 觸發
 * - 後端部署/重啟 → health 的 serverStartTime 改變 → 觸發
 * - buildTime=0（開發環境）自動跳過，不誤觸發。
 */
export function useVersionCheck(): boolean {
  const [hasUpdate, setHasUpdate] = useState(false);

  useEffect(() => {
    let frontendBuildTime = 0;
    let backendStartTime = 0;

    // 初始讀取兩個版本基準
    Promise.all([fetchFrontendBuildTime(false), fetchBackendStartTime()]).then(([ft, bt]) => {
      frontendBuildTime = ft;
      backendStartTime = bt;

      // 開發環境（frontendBuildTime=0）不啟動偵測
      if (ft === 0) return;

      async function check() {
        const [latestFt, latestBt] = await Promise.all([
          fetchFrontendBuildTime(true),
          fetchBackendStartTime()
        ]);

        const frontendUpdated = latestFt !== 0 && latestFt > frontendBuildTime;
        const backendUpdated  = latestBt  !== 0 && latestBt  > backendStartTime;

        if (frontendUpdated || backendUpdated) {
          setHasUpdate(true);
        }
      }

      // 首次 20s 後 check，之後每 5 分鐘
      const first = setTimeout(check, 20_000);
      const interval = setInterval(check, POLL_MS);

      return () => {
        clearTimeout(first);
        clearInterval(interval);
      };
    });
  }, []);

  return hasUpdate;
}
