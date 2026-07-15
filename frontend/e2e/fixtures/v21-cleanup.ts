/** V21 cleanup 的相依操作，讓 assignment 與 model deletion 的先後契約可單元測試。 */
export interface V21CleanupOperations<TSettings> {
  readSettings: () => Promise<TSettings>;
  clearAssignments: (settings: TSettings) => Promise<void>;
  removeModels: (settings: TSettings) => Promise<void>;
}

/**
 * 執行 V21 E2E 清理；只有 assignment 明確清除成功後才允許刪除模型。
 * 所有已允許執行的步驟會完成後再彙整錯誤，避免留下 dangling assignment。
 */
export async function executeV21Cleanup<TSettings>(operations: V21CleanupOperations<TSettings>): Promise<void> {
  const cleanupErrors: string[] = [];
  let settings: TSettings | null = null;
  let assignmentCleanupSucceeded = false;

  try {
    settings = await operations.readSettings();
  } catch (error) {
    cleanupErrors.push(error instanceof Error ? error.message : String(error));
  }

  if (settings != null) {
    try {
      await operations.clearAssignments(settings);
      assignmentCleanupSucceeded = true;
    } catch (error) {
      cleanupErrors.push(error instanceof Error ? error.message : String(error));
    }
  }

  if (settings != null && assignmentCleanupSucceeded) {
    try {
      await operations.removeModels(settings);
    } catch (error) {
      cleanupErrors.push(error instanceof Error ? error.message : String(error));
    }
  }

  if (cleanupErrors.length > 0) {
    throw new Error(`V21 E2E cleanup 失敗：${cleanupErrors.join("；")}`);
  }
}
