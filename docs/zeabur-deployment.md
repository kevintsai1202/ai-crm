# Zeabur 部署資訊

本檔記錄 ai-crm 在 Zeabur 的部署座標，供後續 redeploy / 維運使用。
（redeploy 必須帶 `--service-id`，否則會建立重複服務。）

## 專案

- 專案名稱：`ai-crm`
- Project ID：`6a361651558aac447d435cdc`
- 伺服器（region）：東京 Server（Tencent Cloud, Tokyo）`server-69c0fc8a4fe7cb44c896c3e5`
- Dashboard：https://zeabur.com/projects/6a361651558aac447d435cdc

## 服務

| 服務 | Service ID | 根目錄 | 對外網址 |
|------|------------|--------|----------|
| postgresql（pgvector pg18） | `6a3616f4558aac447d435ce0` | — | 內部 `postgresql.zeabur.internal:5432`，DB `zeabur`，user `root` |
| backend（Spring Boot, Java 21） | `6a36197b558aac447d435d2d` | `/backend` | https://aicrm-backend-kt2026.zeabur.app |
| frontend（React + Vite 靜態） | `6a361b3fb2350c13adc3f305` | `/frontend` | https://aicrm-frontend-kt2026.zeabur.app |

## 關鍵環境變數

### backend
- `SERVER_PORT=${PORT}`（讓 Spring 監聽 Zeabur 指派的埠）
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgresql.zeabur.internal:5432/zeabur`
- `SPRING_DATASOURCE_USERNAME=root` / `SPRING_DATASOURCE_PASSWORD=<DB 密碼>`
- `ZBPACK_JDK_VERSION=21`（zbpack 預設 JDK 太舊，須指定 21）
- `APP_CORS_ALLOWED_ORIGINS=https://aicrm-frontend-kt2026.zeabur.app`
- `APP_SECURITY_JWT_SECRET=<高強度隨機值，≥32 bytes>`（**必填**；未設或為舊公開預設值時應用會 fail-fast 拒絕啟動。修補 commit 8e2da11）
- AI 金鑰：`OPENAI_API_KEY` / `OPENAI_CHAT_MODEL` / `BASE_URL` / `VOYAGE_API_KEY` / `VOYAGE_MODEL` / `VOYAGE_URL`
- **`BASE_URL` 必須含 `/v1`**（Spring AI 2.0 不自動補）；勿設成空字串（應用會正規化為 OpenAI 預設，但閘道情境應明確設值）
- 展示站可 `DEMO_RESET_ENABLED=true`；若使用 `SPRING_PROFILES_ACTIVE=prod` 則強制關閉清除重建且不註冊 `/api/dev/**`
- 建議正式：`SPRING_PROFILES_ACTIVE=prod`

### frontend
- `VITE_API_BASE_URL=https://aicrm-backend-kt2026.zeabur.app/api`（建置期烘入 bundle）

## 重要踩雷紀錄

1. **monorepo 根目錄**：repo 根有聚合 `pom.xml`（`<module>backend</module>`），zbpack 從根建置會誤建 backend。
   `ZBPACK_APP_DIR` 在此「java 根 + node 子目錄」混合 monorepo **不被尊重**；必須用 Zeabur 服務的
   **原生 Root Directory** 設定（Dashboard）。兩者並存會造成 `frontend/frontend` 雙層巢狀路徑錯誤。
2. **Java 版本**：未設 `ZBPACK_JDK_VERSION=21` 會出現 `release version 21 not supported` 編譯失敗。
3. **CORS**：原本寫死 localhost，已改為 `app.cors.allowed-origins`（環境變數）驅動。
4. **埠對齊**：Spring `server.port` 寫死 18080，需 `SERVER_PORT=${PORT}` 對齊 Zeabur 指派埠。

## redeploy 範例

```powershell
# 程式碼推到 GitHub main 後會自動重建；手動重建：
npx zeabur@latest service redeploy --id <service-id> -y -i=false
```
