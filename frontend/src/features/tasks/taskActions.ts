interface TaskActionOptions {
  key: string;
  pending: Set<string>;
  action: () => Promise<unknown>;
  recover: () => Promise<unknown>;
  onError: (message: string) => void;
  onPendingChange: (pending: ReadonlySet<string>) => void;
}

/** 從 HTTP 錯誤物件安全讀取狀態碼。 */
function responseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null || !("response" in error)) return undefined;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : undefined;
}

/** 將任務動作錯誤轉為使用者可理解的訊息。 */
export function taskActionErrorMessage(error: unknown): string {
  if (responseStatus(error) === 409) return "任務已被其他使用者更新，已重新載入最新資料，請再試一次。";
  if (responseStatus(error) === 403) return "你沒有權限執行此任務動作，已重新載入最新資料。";
  return "任務動作失敗，已重新載入最新資料，請稍後再試。";
}

/** 序列化同一任務的 UI 動作，失敗時重新載入以恢復正式 API 狀態。 */
export async function executeTaskAction(options: TaskActionOptions): Promise<boolean> {
  if (options.pending.has(options.key)) return false;
  options.pending.add(options.key);
  options.onPendingChange(new Set(options.pending));
  try {
    await options.action();
    options.onError("");
    return true;
  } catch (error) {
    const actionError = taskActionErrorMessage(error);
    try {
      await options.recover();
      options.onError(actionError);
    } catch {
      options.onError("任務動作失敗，且無法重新載入最新資料，請手動按重新整理。");
    }
    return false;
  } finally {
    options.pending.delete(options.key);
    options.onPendingChange(new Set(options.pending));
  }
}
