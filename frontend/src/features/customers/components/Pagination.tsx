/**
 * 分頁控制元件。
 */
export function Pagination({ page, totalPages, totalElements, onPageChange }: { page: number; totalPages: number; totalElements: number; onPageChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button type="button" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>上一頁</button>
      <span>{page + 1} / {totalPages}（共 {totalElements} 筆）</span>
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>下一頁</button>
    </div>
  );
}
