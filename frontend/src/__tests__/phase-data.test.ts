import type { APIRequestContext } from "@playwright/test";
import { describe, expect, it, vi } from "vitest";
import { cleanupPhaseData, newPhaseDataPrefix } from "../../e2e/fixtures/phase-data";

/** 建立 cleanup 測試使用的最小 API response stub。 */
function response(ok: boolean, status: number, body?: unknown) {
  return {
    ok: () => ok,
    status: () => status,
    json: async () => body,
  };
}

/** 將局部 mock 轉成 Playwright APIRequestContext，避免測試依賴無關方法。 */
function requestContext(get: ReturnType<typeof vi.fn>, deleteRequest: ReturnType<typeof vi.fn>): APIRequestContext {
  return { get, delete: deleteRequest } as unknown as APIRequestContext;
}

describe("phase data fixture", () => {
  it("產生合法階段與 17 位 UTC timestamp 的唯一前綴", () => {
    expect(newPhaseDataPrefix(" v21 ")).toMatch(/^E2E_V21_\d{17}_$/);
    expect(() => newPhaseDataPrefix("V28")).toThrow("不支援的 E2E 階段");
  });

  it("拒絕不安全 prefix，且不呼叫 API", async () => {
    const get = vi.fn();
    const deleteRequest = vi.fn();

    await expect(cleanupPhaseData(requestContext(get, deleteRequest), "E2E_V21_")).rejects.toThrow("拒絕使用不安全");
    expect(get).not.toHaveBeenCalled();
    expect(deleteRequest).not.toHaveBeenCalled();
  });

  it("逐頁查詢並再次過濾名稱，只刪除完整 prefix 資料", async () => {
    const prefix = "E2E_V21_20260715000000000_";
    const get = vi
      .fn()
      .mockResolvedValueOnce(response(true, 200, {
        items: [{ id: 11, name: `${prefix}甲` }, { id: 12, name: "一般客戶" }],
        totalPages: 2,
      }))
      .mockResolvedValueOnce(response(true, 200, {
        items: [{ id: 13, name: `${prefix}乙` }],
        totalPages: 2,
      }));
    const deleteRequest = vi.fn().mockResolvedValue(response(true, 204));

    await cleanupPhaseData(requestContext(get, deleteRequest), prefix);

    expect(get).toHaveBeenNthCalledWith(1, "/api/customers", { params: { keyword: prefix, page: 0, size: 100 } });
    expect(get).toHaveBeenNthCalledWith(2, "/api/customers", { params: { keyword: prefix, page: 1, size: 100 } });
    expect(deleteRequest).toHaveBeenCalledTimes(2);
    expect(deleteRequest).toHaveBeenNthCalledWith(1, "/api/customers/11");
    expect(deleteRequest).toHaveBeenNthCalledWith(2, "/api/customers/13");
  });

  it("ADMIN context 權限不足時提供明確契約訊息", async () => {
    const get = vi.fn().mockResolvedValue(response(false, 403));
    const deleteRequest = vi.fn();

    await expect(cleanupPhaseData(requestContext(get, deleteRequest), "E2E_V21_20260715000000000_"))
      .rejects.toThrow("ADMIN");
  });

  it("DELETE 失敗時回報 customer id 與 HTTP status", async () => {
    const prefix = "E2E_V22_20260715000000000_";
    const get = vi.fn().mockResolvedValue(response(true, 200, {
      items: [{ id: 99, name: `${prefix}失敗案例` }],
      totalPages: 1,
    }));
    const deleteRequest = vi.fn().mockResolvedValue(response(false, 403));

    await expect(cleanupPhaseData(requestContext(get, deleteRequest), prefix))
      .rejects.toThrow("customerId=99，HTTP 403");
  });
});
