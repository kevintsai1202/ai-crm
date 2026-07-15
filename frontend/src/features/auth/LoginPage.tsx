import { FormEvent, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../context/AuthContext";
import { LanguageSwitcher } from "../../components/common/LanguageSwitcher";

/**
 * 登入頁：已登入自動導向儀表板，否則顯示教學帳號登入表單。
 * i18n 試點頁：所有可視文字改由 t() 取得，右上角提供語言切換。
 */
export function LoginPage() {
  const { login, isAuthed } = useAuth();
  const { t } = useTranslation();
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
        setError(t("login.error.invalid"));
      } else if (status === 429) {
        setError(t("login.error.rateLimited"));
      } else if (detail) {
        setError(t("login.error.failedDetail", { detail }));
      } else if (!ax?.response) {
        setError(t("login.error.noBackend"));
      } else {
        setError(t("login.error.generic"));
      }
    }
  }

  return (
    <section className="login-panel">
      <div className="login-copy">
        {/* 語言切換：登入為公開頁，未登入者亦可切換介面語言 */}
        <LanguageSwitcher className="lang-switcher" />
        <span>{t("login.badge")}</span>
        <h2>{t("login.title")}</h2>
        <p>{t("login.intro")}</p>
      </div>
      <form className="login-form" onSubmit={handleLogin}>
        <label>
          {t("login.username")}
          <input name="username" defaultValue="sales@aurora.local" autoComplete="username" />
        </label>
        <label>
          {t("login.password")}
          <input name="password" type="password" defaultValue="password123" autoComplete="current-password" />
        </label>
        {error ? <div className="error-box">{error}</div> : null}
        <button type="submit">{t("login.submit")}</button>
        <small>{t("login.accounts")}</small>
        {/* 募資課程問卷：新分頁開啟，避免離開登入流程時遺失表單狀態 */}
        <a
          className="survey-link"
          href="https://survey.springai.world/"
          target="_blank"
          rel="noopener noreferrer"
        >
          {t("login.surveyLink")}
        </a>
      </form>
    </section>
  );
}
