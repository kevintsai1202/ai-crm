import { FormEvent, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

/**
 * 登入頁：已登入自動導向儀表板，否則顯示教學帳號登入表單。
 */
export function LoginPage() {
  const { login, isAuthed } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");

  // 已登入則直接導向儀表板
  if (isAuthed) return <Navigate to="/dashboard" replace />;

  /** 處理登入表單送出。 */
  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await login(String(form.get("username")), String(form.get("password")));
      navigate("/dashboard", { replace: true });
    } catch (e: unknown) {
      // 顯示較具體錯誤（網路／HTTP），方便本機除錯
      const ax = e as { response?: { status?: number; data?: { detail?: string } }; message?: string };
      const detail = ax?.response?.data?.detail;
      const status = ax?.response?.status;
      if (status === 401) {
        setError("帳號或密碼錯誤。");
      } else if (status === 429) {
        setError("請求過於頻繁，請稍後再試。");
      } else if (detail) {
        setError(`登入失敗：${detail}`);
      } else if (!ax?.response) {
        setError("無法連線後端，請確認服務已啟動（18080）且前端代理正常。");
      } else {
        setError("登入失敗，請確認帳號與密碼。");
      }
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
        {/* 募資課程問卷：新分頁開啟，避免離開登入流程時遺失表單狀態 */}
        <a
          className="survey-link"
          href="https://survey.springai.world/"
          target="_blank"
          rel="noopener noreferrer"
        >
          📋 填寫募資課程問卷
        </a>
      </form>
    </section>
  );
}
