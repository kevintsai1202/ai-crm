export interface V22CleanupTask {
  id: number;
  customerId: number;
  title: string;
  version: number;
}

interface V22CleanupOptions {
  prefix: string;
  customerId: number | null;
  listTasks: () => Promise<V22CleanupTask[]>;
  deleteTask: (id: number, version: number) => Promise<void>;
  deleteCustomer: (id: number) => Promise<void>;
}

/** 僅挑選本次 exact prefix 且屬於指定客戶的 V22 任務。 */
function matchingTasks(tasks: V22CleanupTask[], prefix: string, customerId: number): V22CleanupTask[] {
  return tasks.filter((task) => task.customerId === customerId && task.title.startsWith(prefix));
}

/** 清除 V22 E2E 聚合資料；任務未清乾淨時絕不刪客戶，並彙整所有錯誤。 */
export async function executeV22Cleanup(options: V22CleanupOptions): Promise<void> {
  if (!/^E2E_V22_\d{17}_$/.test(options.prefix)) throw new Error(`拒絕不安全的 V22 prefix：${options.prefix}`);
  if (options.customerId === null) return;
  const errors: string[] = [];
  let discovered: V22CleanupTask[] = [];
  try {
    discovered = matchingTasks(await options.listTasks(), options.prefix, options.customerId);
  } catch (error) {
    errors.push(`list tasks: ${String(error)}`);
  }

  for (const task of discovered) {
    try { await options.deleteTask(task.id, task.version); }
    catch (error) { errors.push(`task ${task.id}: ${String(error)}`); }
  }

  let remaining: V22CleanupTask[] | null = null;
  try { remaining = matchingTasks(await options.listTasks(), options.prefix, options.customerId); }
  catch (error) { errors.push(`verify tasks: ${String(error)}`); }

  if (remaining === null || remaining.length > 0) {
    errors.push(remaining === null ? "無法確認任務已清空，禁止刪除客戶" : `仍有 ${remaining.length} 筆任務，禁止刪除客戶`);
  } else {
    try { await options.deleteCustomer(options.customerId); }
    catch (error) { errors.push(`customer ${options.customerId}: ${String(error)}`); }
  }
  if (errors.length > 0) throw new Error(`V22 cleanup 失敗：${errors.join("；")}`);
}
