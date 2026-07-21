/**
 * 純呈現麵包屑：以 › 分隔逐層渲染；有 onClick 的層級為可點按鈕，無 onClick 為當前頁文字。
 */
export interface Crumb {
  /** 顯示文字 */
  label: string;
  /** 點擊行為；有 onClick 即為可點層級，最後一層通常不帶 */
  onClick?: () => void;
}

export function Breadcrumb({ crumbs }: { crumbs: Crumb[] }) {
  const { t } = useTranslation("common");
  return (
    <nav className="breadcrumb" aria-label={t("breadcrumb")}>
      {crumbs.map((c, i) => (
        <span key={i}>
          {i > 0 ? <span className="breadcrumb-sep">›</span> : null}
          {c.onClick ? (
            <button type="button" className="breadcrumb-link" onClick={c.onClick}>{c.label}</button>
          ) : (
            <span className="breadcrumb-current">{c.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
import { useTranslation } from "react-i18next";
