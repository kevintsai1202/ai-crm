import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useVersionCheck } from "../hooks/useVersionCheck";
import type { HealthResponse } from "../types";
import { formatDateTime } from "../lib/format";

/**
 * 顯示後端連線狀態，任何錯誤都以紅燈呈現。
 */
function HealthBadge({ health, error, onRefresh }: { health: HealthResponse | null; error: boolean; onRefresh: () => void }) {
  const ok = !!health && !error;
  return (
    <div className={`health-card ${ok ? "ok" : "fail"}`}>
      <span className="pulse" />
      <div>
        <strong>{ok ? "後端連線正常" : "後端無法連線"}</strong>
        <small>{health?.timestamp ? formatDateTime(health.timestamp) : "尚未取得健康資訊"}</small>
      </div>
      <button type="button" onClick={onRefresh}>重測</button>
    </div>
  );
}

/**
 * 系統更新通知橫幅：偵測到新版本時顯示於頂部，引導使用者重新整理。
 * 函式級註解：「立即更新」會清除 JWT token 並強制重載，確保新版本 API 與前端同步。
 */
function UpdateBanner() {
  function handleUpdate() {
    // cookie 由後端管理，直接重載即可
    window.location.reload();
  }

  return (
    <div className="update-banner">
      <span className="update-banner__icon">🚀</span>
      <span className="update-banner__text">
        系統已更新，請重新整理以使用最新功能
      </span>
      <button type="button" className="update-banner__btn" onClick={handleUpdate}>
        立即更新
      </button>
    </div>
  );
}

/**
 * 應用外殼：左側邊欄（品牌 + 健康狀態 + 使用者卡 + 導覽）+ 右側 <Outlet/>。
 * 函式級註解：側邊欄導覽提供「儀表板」與「客戶」兩個主入口，達成儀表板與操作分頁。
 */
export function AppShell() {
  const { user, health, healthError, refreshHealth, logout } = useAuth();
  const hasUpdate = useVersionCheck();

  return (
    <div className="app-shell">
      {hasUpdate && <UpdateBanner />}
      <aside className="sidebar">
        <div className="brand-block">
          <img src="/crm-hero.svg" alt="AI CRM 工作台視覺" />
          <div>
            <span>AI CRM</span>
            <h1>智慧業務助理</h1>
          </div>
        </div>
        <nav className="side-nav">
          <NavLink to="/dashboard" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>📊 儀表板</NavLink>
          <NavLink to="/my-work" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>🧑‍💼 我的工作台</NavLink>
          <NavLink to="/customers" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>👥 客戶工作台</NavLink>
          {user?.role === "MANAGER" || user?.role === "ADMIN" ? (
            <NavLink to="/team" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>📈 業務分析</NavLink>
          ) : null}
          {user?.role === "ADMIN" ? (
            <>
              <NavLink to="/admin/users" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>⚙️ 帳號管理</NavLink>
              <NavLink to="/admin/settings" className={({ isActive }) => isActive ? "side-nav-link active" : "side-nav-link"}>🔧 系統設定</NavLink>
            </>
          ) : null}
        </nav>
        <HealthBadge health={health} error={healthError} onRefresh={refreshHealth} />
        {user ? (
          <div className="user-card">
            <strong>{user.displayName}</strong>
            <span>{user.role}</span>
            <button type="button" onClick={logout}>登出</button>
          </div>
        ) : null}
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
