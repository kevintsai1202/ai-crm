import type { APIRequestContext } from "@playwright/test";

/** 客戶搜尋 API 回傳的最小欄位。 */
interface CustomerSummary {
  id: number;
  name: string;
}

/** 客戶分頁 API 回傳的最小結構。 */
interface CustomerPage {
  items: CustomerSummary[];
  totalPages: number;
}

/**
 * 建立可辨識且適合清理的階段測試資料前綴。
 *
 * @param phase 階段代號，例如 V21
 * @returns 格式為 E2E_<PHASE>_<UTC timestamp>_ 的前綴
 */
export function newPhaseDataPrefix(phase: string): string {
  const normalizedPhase = phase.trim().toUpperCase();
  if (!/^V2[1-7]$/.test(normalizedPhase)) {
    throw new Error(`不支援的 E2E 階段：${phase}`);
  }

  // 使用 ISO UTC 時間移除標點，保留毫秒以降低平行測試名稱碰撞。
  const utcTimestamp = new Date().toISOString().replace(/[-:.TZ]/g, "");
  return `E2E_${normalizedPhase}_${utcTimestamp}_`;
}

/**
 * 刪除指定前綴建立的客戶資料；客戶的聯絡人、互動與商機由後端既有 cascade 一併清除。
 *
 * @param request 已具備登入狀態的 Playwright API request context
 * @param prefix 本次測試由 newPhaseDataPrefix 建立的唯一前綴
 */
export async function cleanupPhaseData(request: APIRequestContext, prefix: string): Promise<void> {
  if (!/^E2E_V2[1-7]_\d{17}_$/.test(prefix)) {
    throw new Error(`拒絕使用不安全的 E2E 資料前綴：${prefix}`);
  }

  let page = 0;
  let totalPages = 1;
  const matchedCustomers: CustomerSummary[] = [];

  while (page < totalPages) {
    const response = await request.get("/api/customers", {
      params: { keyword: prefix, page, size: 100 },
    });
    if (!response.ok()) {
      throw new Error(`查詢 E2E 客戶資料失敗：HTTP ${response.status()}`);
    }

    const customerPage = (await response.json()) as CustomerPage;
    matchedCustomers.push(...customerPage.items.filter((customer) => customer.name.startsWith(prefix)));
    totalPages = customerPage.totalPages;
    page += 1;
  }

  for (const customer of matchedCustomers) {
    // 即使後端 keyword 搜尋行為改變，也只允許刪除名稱符合本次唯一前綴的資料。
    if (!customer.name.startsWith(prefix)) continue;
    const response = await request.delete(`/api/customers/${customer.id}`);
    if (!response.ok()) {
      throw new Error(`刪除 E2E 客戶資料失敗：customerId=${customer.id}，HTTP ${response.status()}`);
    }
  }
}
