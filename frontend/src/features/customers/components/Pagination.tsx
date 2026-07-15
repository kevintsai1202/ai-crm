import { useTranslation } from "react-i18next";

/**
 * 分頁控制元件。
 */
export function Pagination({ page, totalPages, totalElements, onPageChange }: { page: number; totalPages: number; totalElements: number; onPageChange: (p: number) => void }) {
  const { t } = useTranslation(["customers", "common"]);
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button type="button" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>{t("common:pagination.prev")}</button>
      <span>{t("customers:pagination.pageOfWithTotal", { page: page + 1, total: totalPages, count: totalElements })}</span>
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>{t("common:pagination.next")}</button>
    </div>
  );
}
