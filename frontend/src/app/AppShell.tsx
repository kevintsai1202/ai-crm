import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../context/AuthContext";
import { useVersionCheck } from "../hooks/useVersionCheck";
import type { HealthResponse } from "../types";
import { formatDateTime } from "../lib/format";
import { LanguageSwitcher } from "../components/common/LanguageSwitcher";

/**
 * 顯示後端連線狀態，任何錯誤都以紅燈呈現。
 */
function HealthBadge({ health, error, onRefresh }: { health: HealthResponse | null; error: boolean; onRefresh: () => void }) {
  const { t, i18n } = useTranslation("app");
  const ok = !!health && !error;
  return (
    <div className={`health-card ${ok ? "ok" : "fail"}`}>
      <span className="pulse" />
      <div>
        <strong>{ok ? t("health.ok") : t("health.fail")}</strong>
        <small>{health?.timestamp ? formatDateTime(health.timestamp, i18n.language, t("common:noData")) : t("health.noData")}</small>
      </div>
      <button type="button" onClick={onRefresh}>{t("health.retry")}</button>
    </div>
  );
}

/**
 * 系統更新通知橫幅：偵測到新版本時顯示於頂部，引導使用者重新整理。
 * 函式級註解：「立即更新」會清除 JWT token 並強制重載，確保新版本 API 與前端同步。
 */
function UpdateBanner() {
  const { t } = useTranslation("app");

  function handleUpdate() {
    // cookie 由後端管理，直接重載即可
    window.location.reload();
  }

  return (
    <div className="update-banner">
      <span className="update-banner__icon">🚀</span>
      <span className="update-banner__text">
        {t("updateBanner.message")}
      </span>
      <button type="button" className="update-banner__btn" onClick={handleUpdate}>
        {t("updateBanner.action")}
      </button>
    </div>
  );
}

/**
 * 應用外殼：左側邊欄（品牌 + 健康狀態 + 使用者卡 + 導覽）+ 右側 <Outlet/>。
 * 函式級註解：側邊欄導覽提供「儀表板」與「客戶」兩個主入口，達成儀表板與操作分頁。
 */
export function AppShell() {
  const { t } = useTranslation(["app", "common"]);
  const { user, health, healthError, refreshHealth, logout } = useAuth();
  const hasUpdate = useVersionCheck();
  // 側邊欄收合偏好只影響桌面版，並保留於瀏覽器供下次進入沿用。
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    () => localStorage.getItem("ai-crm-sidebar-collapsed") === "true"
  );
  // 手機導覽預設收合，避免選單長期佔據首屏；桌面收合偏好與此狀態互不影響。
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const mobileMenuButtonRef = useRef<HTMLButtonElement>(null);
  const mobileMenuPanelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (mobileMenuOpen) {
      mobileMenuPanelRef.current?.querySelector<HTMLAnchorElement>("a")?.focus();
    }
  }, [mobileMenuOpen]);

  /** 切換側邊欄展開狀態並保存使用者偏好。 */
  function toggleSidebar() {
    setSidebarCollapsed((current) => {
      const next = !current;
      localStorage.setItem("ai-crm-sidebar-collapsed", String(next));
      return next;
    });
  }

  /** 切換手機浮動導覽面板。 */
  function toggleMobileMenu() {
    setMobileMenuOpen((current) => !current);
  }

  /** 收合手機導覽；由背景或 Escape 關閉時將焦點送回選單按鈕。 */
  function closeMobileMenu(restoreFocus = false) {
    setMobileMenuOpen(false);
    if (restoreFocus) {
      mobileMenuButtonRef.current?.focus();
    }
  }

  /** 讓鍵盤使用者可按 Escape 關閉手機選單。 */
  function handleSidebarKeyDown(event: React.KeyboardEvent<HTMLElement>) {
    if (event.key === "Escape" && mobileMenuOpen) {
      event.preventDefault();
      closeMobileMenu(true);
    }
  }

  /** 登出前先關閉手機選單，避免登入頁仍殘留浮動面板狀態。 */
  function handleLogout() {
    closeMobileMenu();
    logout();
  }

  return (
    <div className={`app-shell${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
      {hasUpdate && <UpdateBanner />}
      <aside
        className={`sidebar${sidebarCollapsed ? " collapsed" : ""}${mobileMenuOpen ? " mobile-menu-open" : ""}`}
        onKeyDown={handleSidebarKeyDown}
      >
        <div className="sidebar-toolbar">
          <LanguageSwitcher className="lang-switcher sidebar-language" />
          <button
            type="button"
            className="sidebar-toggle"
            aria-label={t(sidebarCollapsed ? "app:menu.expand" : "app:menu.collapse")}
            aria-expanded={!sidebarCollapsed}
            onClick={toggleSidebar}
          >
            <span aria-hidden="true">{sidebarCollapsed ? "☰" : "◀"}</span>
          </button>
          <button
            ref={mobileMenuButtonRef}
            type="button"
            className="mobile-menu-toggle"
            aria-label={t(mobileMenuOpen ? "app:menu.closeMobile" : "app:menu.openMobile")}
            aria-expanded={mobileMenuOpen}
            aria-controls="mobile-navigation"
            onClick={toggleMobileMenu}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              {mobileMenuOpen ? (
                <path d="M6 6l12 12M18 6 6 18" />
              ) : (
                <path d="M4 7h16M4 12h16M4 17h16" />
              )}
            </svg>
          </button>
        </div>
        <div className="brand-block">
          <img src="/crm-hero.svg" alt={t("app:brand.tagline")} />
          <div className="brand-copy">
            <span>{t("app:brand.name")}</span>
            <h1>{t("app:brand.tagline")}</h1>
          </div>
        </div>
        {mobileMenuOpen ? <div className="mobile-menu-backdrop" aria-hidden="true" onClick={() => closeMobileMenu(true)} /> : null}
        <div id="mobile-navigation" ref={mobileMenuPanelRef} className="sidebar-content">
          <nav className="side-nav" aria-label={t("app:menu.navigationLabel")}>
            <NavLink to="/dashboard" title={t("app:nav.dashboard")} onClick={() => closeMobileMenu()} className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}><span className="side-nav-icon" aria-hidden="true">📊</span><span className="side-nav-label">{t("app:nav.dashboard")}</span></NavLink>
            <NavLink to="/customers" title={t("app:nav.customers")} onClick={() => closeMobileMenu()} className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}><span className="side-nav-icon" aria-hidden="true">👥</span><span className="side-nav-label">{t("app:nav.customers")}</span></NavLink>
            {user?.role === "MANAGER" || user?.role === "ADMIN" ? (
              <NavLink to="/team" title={t("app:nav.team")} onClick={() => closeMobileMenu()} className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}><span className="side-nav-icon" aria-hidden="true">📈</span><span className="side-nav-label">{t("app:nav.team")}</span></NavLink>
            ) : null}
            {user?.role === "ADMIN" ? (
              <>
                <NavLink to="/admin/users" title={t("app:nav.adminUsers")} onClick={() => closeMobileMenu()} className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}><span className="side-nav-icon" aria-hidden="true">⚙️</span><span className="side-nav-label">{t("app:nav.adminUsers")}</span></NavLink>
                <NavLink to="/admin/settings" title={t("app:nav.adminSettings")} onClick={() => closeMobileMenu()} className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}><span className="side-nav-icon" aria-hidden="true">🔧</span><span className="side-nav-label">{t("app:nav.adminSettings")}</span></NavLink>
              </>
            ) : null}
          </nav>
          <HealthBadge health={health} error={healthError} onRefresh={refreshHealth} />
          {user ? (
            <div className="user-card">
              <strong>{user.displayName}</strong>
              <span>{user.role}</span>
              <button type="button" onClick={handleLogout}>{t("app:userCard.logout")}</button>
            </div>
          ) : null}
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
