import { FormEvent, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { getToken } from "../../api";
import { useAuth } from "../../context/AuthContext";

/**
 * 登入頁：已登入自動導向儀表板，否則顯示教學帳號登入表單。
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");

  // 已登入則直接導向儀表板
  if (getToken()) return <Navigate to="/dashboard" replace />;

  /** 處理登入表單送出。 */
  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await login(String(form.get("username")), String(form.get("password")));
      navigate("/dashboard");
    } catch {
      setError("登入失敗，請確認帳號與密碼。");
    }
  }

  return (
    <section className="login-panel">
      <div className="login-copy">
        <span>Unit 4 + Unit 5</span>
        <h2>登入 AI CRM 工作台</h2>
        <p>使用教學 seed 帳號進入完整工作台，驗證 JWT、角色權限、Dashboard、客戶資料、AI 助理與 Agent Trace。</p>
      </div>
      <form className="login-form" onSubmit={handleLogin}>
        <label>
          帳號
          <input name="username" defaultValue="sales@aurora.local" autoComplete="username" />
        </label>
        <label>
          密碼
          <input name="password" type="password" defaultValue="password123" autoComplete="current-password" />
        </label>
        {error ? <div className="error-box">{error}</div> : null}
        <button type="submit">登入</button>
        <small>可用帳號：sales@aurora.local / manager@aurora.local / admin@aurora.local，密碼皆為 password123。</small>
      </form>
    </section>
  );
}
