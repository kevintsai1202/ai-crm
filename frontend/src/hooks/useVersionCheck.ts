import { useEffect, useState } from "react";

/** 從 /build-info.json 讀取本次啟動時的 buildTime（由 Vite plugin 在 build 時寫入）。 */
async function fetchBuildTime(bustCache = false): Promise<number> {
  try {
    // bustCache=true 時加時間戳，強制繞過 CDN / proxy 快取
    const url = bustCache ? `/build-info.json?_t=${Date.now()}` : "/build-info.json";
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) return 0;
    const data = await res.json();
    return typeof data.buildTime === "number" ? data.buildTime : 0;
  } catch {
    return 0;
  }
}

/** 每 5 分鐘輪詢一次新版本；開發環境（buildTime=0）不觸發。 */
const POLL_MS = 5 * 60 * 1000;

/**
 * 版本更新偵測 hook：比對頁面載入時與最新部署的 buildTime。
 * 函式級註解：Vite buildInfoPlugin 在每次 build 時寫入 public/build-info.json，
 * 每次部署後 buildTime 自動遞增；CDN 快取由 `?_t=` query 強制失效。
 * buildTime=0 視為開發環境，不啟動偵測以避免誤觸發。
 */
export function useVersionCheck(): boolean {
  const [hasUpdate, setHasUpdate] = useState(false);

  useEffect(() => {
    let currentBuildTime = 0;

    // 先讀取當前 buildTime（不帶快取破壞，拿的就是本次載入的版本）
    fetchBuildTime(false).then((t) => {
      currentBuildTime = t;
      // buildTime=0 表示開發環境或尚未 build，不啟動偵測
      if (t === 0) return;

      // 首次 20 秒後 check，之後每 5 分鐘
      const first = setTimeout(check, 20_000);
      const interval = setInterval(check, POLL_MS);

      async function check() {
        const latest = await fetchBuildTime(true); // 強制繞過快取
        if (latest !== 0 && latest > currentBuildTime) {
          setHasUpdate(true);
        }
      }

      // 清理
      return () => {
        clearTimeout(first);
        clearInterval(interval);
      };
    });
  }, []);

  return hasUpdate;
}
