import { FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { createAdminUser, fetchAdminUsers, resetAdminUserPassword, setAdminUserEnabled, updateAdminUser } from "../../api";
import type { AdminUser, Role } from "../../types";
import { useAuth } from "../../context/AuthContext";
import { formatDateTime } from "../../lib/format";

/** 角色選項（與後端 Role enum 對應）。 */
const ROLES: Role[] = ["SALES", "MANAGER", "ADMIN"];

/**
 * 帳號管理頁（僅 ADMIN）：列出所有帳號，支援新增、編輯（顯示名稱/角色）、重設密碼、啟用/停用。
 * 函式級註解：所有異動後重新載入清單以反映最新狀態；操作自己的帳號時後端會擋下停用/降級。
 */
export function AdminUsersPage() {
  const { t, i18n } = useTranslation(["operations", "common"]);
  const { user } = useAuth();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // 新增帳號 modal 開關
  const [showCreate, setShowCreate] = useState(false);
  // 編輯中的帳號（null 表示未開）
  const [editing, setEditing] = useState<AdminUser | null>(null);
  // 重設密碼中的帳號（null 表示未開）
  const [resetting, setResetting] = useState<AdminUser | null>(null);

  /** 載入帳號清單。 */
  async function load() {
    setLoading(true);
    setError(null);
    try {
      setUsers(await fetchAdminUsers());
    } catch (e) {
      console.error("Failed to load user accounts:", e);
      setError(t("adminUsers.loadError"));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  /** 啟用/停用切換。 */
  async function toggleEnabled(target: AdminUser) {
    try {
      await setAdminUserEnabled(target.id, !target.enabled);
      await load();
    } catch (e) {
      alert(extractError(e, t("adminUsers.toggleError")));
    }
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>{t("adminUsers.subtitle")}</p>
          <h2>{t("adminUsers.title")}</h2>
        </div>
        <div className="topbar-actions">
          <button type="button" className="btn-assess" onClick={() => setShowCreate(true)}>{t("adminUsers.add")}</button>
        </div>
      </section>

      <div className="panel">
        {loading ? (
          <div className="sr-empty">{t("adminUsers.loading")}</div>
        ) : error ? (
          <div className="sr-empty">{error}</div>
        ) : (
          /* 帳號欄位與操作按鈕需要完整保留，窄螢幕只在表格容器內捲動。 */
          <div className="table-scroll admin-users-table-scroll" role="region" aria-label={t("adminUsers.tableLabel")} tabIndex={0}>
            <table className="admin-user-table">
              <thead>
                <tr>
                  <th>{t("adminUsers.columns.username")}</th><th>{t("adminUsers.columns.displayName")}</th><th>{t("adminUsers.columns.role")}</th><th>{t("adminUsers.columns.status")}</th><th>{t("adminUsers.columns.createdAt")}</th><th>{t("adminUsers.columns.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id} className={u.enabled ? "" : "row-disabled"}>
                    <td>{u.username}{u.username === user?.username ? <span className="me-badge">{t("adminUsers.me")}</span> : null}</td>
                    <td>{u.displayName}</td>
                    <td>{t(`adminUsers.roles.${u.role}`)}</td>
                    <td>
                      <span className={`status-pill ${u.enabled ? "on" : "off"}`}>{t(u.enabled ? "adminUsers.enabled" : "adminUsers.disabled")}</span>
                    </td>
                    <td>{formatDateTime(u.createdAt, i18n.language, t("adminUsers.noData"))}</td>
                    <td className="admin-user-actions">
                      <button type="button" className="btn-secondary" onClick={() => setEditing(u)}>{t("adminUsers.edit")}</button>
                      <button type="button" className="btn-secondary" onClick={() => setResetting(u)}>{t("adminUsers.resetPassword")}</button>
                      <button
                        type="button"
                        className="btn-secondary"
                        disabled={u.username === user?.username}
                        title={u.username === user?.username ? t("adminUsers.cannotDisableSelf") : ""}
                        onClick={() => toggleEnabled(u)}
                      >
                        {t(u.enabled ? "adminUsers.disabled" : "adminUsers.enabled")}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showCreate ? (
        <CreateUserModal
          onClose={() => setShowCreate(false)}
          onCreated={async () => { setShowCreate(false); await load(); }}
        />
      ) : null}
      {editing ? (
        <EditUserModal
          target={editing}
          onClose={() => setEditing(null)}
          onSaved={async () => { setEditing(null); await load(); }}
        />
      ) : null}
      {resetting ? (
        <ResetPasswordModal
          target={resetting}
          onClose={() => setResetting(null)}
          onSaved={() => setResetting(null)}
        />
      ) : null}
    </>
  );
}

/** 從 axios 錯誤取出後端訊息。 */
function extractError(e: unknown, fallback: string): string {
  const detail = (e as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
  return detail?.detail ?? detail?.message ?? fallback;
}

/** 新增帳號 Modal。 */
function CreateUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation("operations");
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  async function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    setSubmitting(true);
    setErr(null);
    try {
      await createAdminUser({
        username: String(fd.get("username")),
        displayName: String(fd.get("displayName")),
        role: String(fd.get("role")) as Role,
        password: String(fd.get("password"))
      });
      onCreated();
    } catch (e2) {
      setErr(extractError(e2, t("adminUsers.createError")));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("adminUsers.createTitle")}</h3>
        <label>{t("adminUsers.username")}<input name="username" type="email" required placeholder="user@aurora.local" /></label>
        <label>{t("adminUsers.displayName")} <input name="displayName" required /></label>
        <label>
          {t("adminUsers.role")}
          <select name="role" defaultValue="SALES">
            {ROLES.map((r) => <option key={r} value={r}>{t(`adminUsers.roles.${r}`)}</option>)}
          </select>
        </label>
        <label>{t("adminUsers.initialPassword")} <input name="password" type="password" required minLength={6} /></label>
        {err ? <p className="form-error">{err}</p> : null}
        <div className="modal-actions">
          <button type="submit" disabled={submitting}>{t(submitting ? "adminUsers.creating" : "adminUsers.create")}</button>
          <button type="button" onClick={onClose}>{t("adminUsers.cancel")}</button>
        </div>
      </form>
    </div>
  );
}

/** 編輯帳號（顯示名稱 + 角色）Modal。 */
function EditUserModal({ target, onClose, onSaved }: { target: AdminUser; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation("operations");
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  async function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    setSubmitting(true);
    setErr(null);
    try {
      await updateAdminUser(target.id, { displayName: String(fd.get("displayName")), role: String(fd.get("role")) as Role });
      onSaved();
    } catch (e2) {
      setErr(extractError(e2, t("adminUsers.updateError")));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("adminUsers.editTitle", { username: target.username })}</h3>
        <label>{t("adminUsers.displayName")} <input name="displayName" required defaultValue={target.displayName} /></label>
        <label>
          {t("adminUsers.role")}
          <select name="role" defaultValue={target.role}>
            {ROLES.map((r) => <option key={r} value={r}>{t(`adminUsers.roles.${r}`)}</option>)}
          </select>
        </label>
        {err ? <p className="form-error">{err}</p> : null}
        <div className="modal-actions">
          <button type="submit" disabled={submitting}>{t(submitting ? "adminUsers.saving" : "adminUsers.save")}</button>
          <button type="button" onClick={onClose}>{t("adminUsers.cancel")}</button>
        </div>
      </form>
    </div>
  );
}

/** 重設密碼 Modal。 */
function ResetPasswordModal({ target, onClose, onSaved }: { target: AdminUser; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation("operations");
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  async function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    setSubmitting(true);
    setErr(null);
    try {
      await resetAdminUserPassword(target.id, String(fd.get("password")));
      setDone(true);
    } catch (e2) {
      setErr(extractError(e2, t("adminUsers.resetError")));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>{t("adminUsers.resetTitle", { username: target.username })}</h3>
        {done ? (
          <>
            <p>{t("adminUsers.resetDone")}</p>
            <div className="modal-actions"><button type="button" onClick={onSaved}>{t("adminUsers.close")}</button></div>
          </>
        ) : (
          <>
            <label>{t("adminUsers.newPassword")} <input name="password" type="password" required minLength={6} autoFocus /></label>
            {err ? <p className="form-error">{err}</p> : null}
            <div className="modal-actions">
              <button type="submit" disabled={submitting}>{t(submitting ? "adminUsers.resetting" : "adminUsers.confirmReset")}</button>
              <button type="button" onClick={onClose}>{t("adminUsers.cancel")}</button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}
