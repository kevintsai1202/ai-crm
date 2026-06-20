import { FormEvent, useEffect, useState } from "react";
import { createAdminUser, fetchAdminUsers, resetAdminUserPassword, setAdminUserEnabled, updateAdminUser } from "../../api";
import type { AdminUser, Role } from "../../types";
import { useAuth } from "../../context/AuthContext";
import { formatDateTime } from "../../lib/format";

/** 角色選項（與後端 Role enum 對應）。 */
const ROLES: Role[] = ["SALES", "MANAGER", "ADMIN"];
/** 角色顯示名稱。 */
const ROLE_LABEL: Record<Role, string> = { SALES: "業務", MANAGER: "經理", ADMIN: "管理員" };

/**
 * 帳號管理頁（僅 ADMIN）：列出所有帳號，支援新增、編輯（顯示名稱/角色）、重設密碼、啟用/停用。
 * 函式級註解：所有異動後重新載入清單以反映最新狀態；操作自己的帳號時後端會擋下停用/降級。
 */
export function AdminUsersPage() {
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
      console.error("載入帳號清單失敗:", e);
      setError("載入帳號清單失敗");
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
      alert(extractError(e, "切換狀態失敗"));
    }
  }

  return (
    <>
      <section className="topbar">
        <div>
          <p>Hahow AI Full-stack Teaching Build</p>
          <h2>帳號管理</h2>
        </div>
        <div className="topbar-actions">
          <button type="button" className="btn-assess" onClick={() => setShowCreate(true)}>＋ 新增帳號</button>
        </div>
      </section>

      <div className="panel">
        {loading ? (
          <div className="sr-empty">載入中…</div>
        ) : error ? (
          <div className="sr-empty">{error}</div>
        ) : (
          <table className="admin-user-table">
            <thead>
              <tr>
                <th>帳號</th><th>顯示名稱</th><th>角色</th><th>狀態</th><th>建立時間</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} className={u.enabled ? "" : "row-disabled"}>
                  <td>{u.username}{u.username === user?.username ? <span className="me-badge">我</span> : null}</td>
                  <td>{u.displayName}</td>
                  <td>{ROLE_LABEL[u.role]}</td>
                  <td>
                    <span className={`status-pill ${u.enabled ? "on" : "off"}`}>{u.enabled ? "啟用" : "停用"}</span>
                  </td>
                  <td>{formatDateTime(u.createdAt)}</td>
                  <td className="admin-user-actions">
                    <button type="button" className="btn-secondary" onClick={() => setEditing(u)}>編輯</button>
                    <button type="button" className="btn-secondary" onClick={() => setResetting(u)}>重設密碼</button>
                    <button
                      type="button"
                      className="btn-secondary"
                      disabled={u.username === user?.username}
                      title={u.username === user?.username ? "不可停用自己的帳號" : ""}
                      onClick={() => toggleEnabled(u)}
                    >
                      {u.enabled ? "停用" : "啟用"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
      setErr(extractError(e2, "新增失敗"));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>新增帳號</h3>
        <label>帳號（Email）<input name="username" type="email" required placeholder="user@aurora.local" /></label>
        <label>顯示名稱 <input name="displayName" required /></label>
        <label>
          角色
          <select name="role" defaultValue="SALES">
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </label>
        <label>初始密碼 <input name="password" type="password" required minLength={6} /></label>
        {err ? <p className="form-error">{err}</p> : null}
        <div className="modal-actions">
          <button type="submit" disabled={submitting}>{submitting ? "建立中…" : "建立"}</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}

/** 編輯帳號（顯示名稱 + 角色）Modal。 */
function EditUserModal({ target, onClose, onSaved }: { target: AdminUser; onClose: () => void; onSaved: () => void }) {
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
      setErr(extractError(e2, "更新失敗"));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>編輯帳號 — {target.username}</h3>
        <label>顯示名稱 <input name="displayName" required defaultValue={target.displayName} /></label>
        <label>
          角色
          <select name="role" defaultValue={target.role}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </label>
        {err ? <p className="form-error">{err}</p> : null}
        <div className="modal-actions">
          <button type="submit" disabled={submitting}>{submitting ? "儲存中…" : "儲存"}</button>
          <button type="button" onClick={onClose}>取消</button>
        </div>
      </form>
    </div>
  );
}

/** 重設密碼 Modal。 */
function ResetPasswordModal({ target, onClose, onSaved }: { target: AdminUser; onClose: () => void; onSaved: () => void }) {
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
      setErr(extractError(e2, "重設失敗"));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal-content" onClick={(e) => e.stopPropagation()} onSubmit={handle}>
        <h3>重設密碼 — {target.username}</h3>
        {done ? (
          <>
            <p>密碼已重設完成。</p>
            <div className="modal-actions"><button type="button" onClick={onSaved}>關閉</button></div>
          </>
        ) : (
          <>
            <label>新密碼 <input name="password" type="password" required minLength={6} autoFocus /></label>
            {err ? <p className="form-error">{err}</p> : null}
            <div className="modal-actions">
              <button type="submit" disabled={submitting}>{submitting ? "重設中…" : "確認重設"}</button>
              <button type="button" onClick={onClose}>取消</button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}
