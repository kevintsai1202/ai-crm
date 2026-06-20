import { useEffect, useState } from "react";

/**
 * 通用前端分頁清單：把超量資料切成固定每頁筆數，底部提供上一頁/下一頁。
 * 函式級註解：純前端分頁（資料已在記憶體），用於儀表板清單卡固定高度；
 * 資料量小於等於 pageSize 時不顯示分頁列。
 */
interface PaginatedListProps<T> {
  /** 全部資料 */
  items: T[];
  /** 每頁筆數，預設 5 */
  pageSize?: number;
  /** 單列渲染 */
  renderRow: (item: T, index: number) => React.ReactNode;
  /** React key 產生器 */
  rowKey: (item: T, index: number) => string;
  /** 無資料時顯示文字 */
  emptyText?: string;
}

export function PaginatedList<T>({ items, pageSize = 5, renderRow, rowKey, emptyText = "尚無資料" }: PaginatedListProps<T>) {
  // 當前頁碼（0 起算）
  const [page, setPage] = useState(0);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));

  // 資料量變動導致當前頁超界時，夾回最後一頁，避免顯示空白頁（函數式更新，只需依賴 totalPages）
  useEffect(() => {
    setPage((p) => Math.min(p, totalPages - 1));
  }, [totalPages]);

  if (items.length === 0) {
    return <div className="sr-empty">{emptyText}</div>;
  }

  const start = page * pageSize;
  const pageItems = items.slice(start, start + pageSize);

  return (
    <div className="paginated-list">
      <div className="sr-list">
        {pageItems.map((item, i) => (
          <div key={rowKey(item, start + i)}>{renderRow(item, start + i)}</div>
        ))}
      </div>
      {items.length > pageSize ? (
        <div className="pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>上一頁</button>
          <span>第 {page + 1} / {totalPages} 頁</span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}>下一頁</button>
        </div>
      ) : null}
    </div>
  );
}
