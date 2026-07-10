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

### backend（既有，redeploy 後仍必填／建議維持）

| 變數 | 必要性 | 說明 |
|------|--------|------|
| `SERVER_PORT=${PORT}` | 必填 | 對齊 Zeabur 指派埠 |
| `SPRING_DATASOURCE_URL` | 必填 | `jdbc:postgresql://postgresql.zeabur.internal:5432/zeabur` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | 必填 | 例：`root` / DB 密碼 |
| `ZBPACK_JDK_VERSION=21` | 必填 | zbpack 預設 JDK 太舊 |
| `APP_SECURITY_JWT_SECRET` | 必填 | ≥32 bytes 隨機值；fail-fast |
| `APP_CORS_ALLOWED_ORIGINS` | 必填 | `https://aicrm-frontend-kt2026.zeabur.app`（對應 `app.cors.allowed-origins`） |
| `OPENAI_API_KEY` / `OPENAI_CHAT_MODEL` / `BASE_URL` | 建議 | AI；**`BASE_URL` 必須含 `/v1`**，勿留空字串 |
| `VOYAGE_API_KEY` / `VOYAGE_MODEL` / `VOYAGE_URL` | 建議 | 真向量 RAG；無則 deterministic embedding |

### backend（SP10–SP15 新增／建議，**非強制改才會動**）

| 變數 | 必要性 | 建議（展示站） | 說明 |
|------|--------|----------------|------|
| `APP_COOKIE_SECURE` | **建議改 true** | `true` | 後端為 HTTPS 時 cookie 應用 `Secure; SameSite=None`。未設預設 `false`（本機 HTTP）；**不改也能用**，因前端主路徑是 Bearer token |
| `DEMO_RESET_ENABLED` | 依定位 | 展示站 `true`；真正式 `false` | 預設 true；僅影響 ADMIN 清除重建 |
| `SPRING_PROFILES_ACTIVE` | 選用 | 展示站**不要**設 `prod` | `prod` 會關 demo reset 且不註冊 `/api/dev/**` |
| `APP_RATE_LIMIT_ENABLED` | 選用 | 可不設（預設 true） | 登入 30/分、AI 60/分／IP |
| `APP_RATE_LIMIT_LOGIN_PER_MINUTE` | 選用 | 課室可調高如 `60` | 預設 30 |
| `APP_RATE_LIMIT_AI_PER_MINUTE` | 選用 | 課室可調高如 `120` | 預設 60 |
| Flyway V19/V20 | **自動** | 無需參數 | redeploy 後自動套 knowledge_chunks、stage_history |

### frontend
- `VITE_API_BASE_URL=https://aicrm-backend-kt2026.zeabur.app/api`（建置期烘入 bundle）
- **本次無需改**（除非後端網域變了）

### 結論（2026-07 SP 後）

- **不必為了新功能大改一堆變數**；push 後 redeploy backend（+ 若前端有 bundle 變更也 redeploy frontend）即可。
- **唯一建議立刻加／改：** 後端 `APP_COOKIE_SECURE=true`（HTTPS 部署較正確）。
- **請確認既有未壞：** `BASE_URL` 含 `/v1`、JWT、CORS、DB、JDK 21。
- **展示站不要** 誤設 `SPRING_PROFILES_ACTIVE=prod`，否則 demo 生成 API 會消失。

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
