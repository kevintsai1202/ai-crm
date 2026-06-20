# API 契約

Base URL：`http://127.0.0.1:18080/api`

## Health

`GET /health`

```json
{
  "status": "UP",
  "timestamp": "2026-06-13T00:00:00Z",
  "features": {
    "database": "JPA",
    "security": "JWT",
    "ai": "Deterministic Teaching Flow"
  }
}
```

## Auth

`POST /auth/login`

```json
{
  "username": "sales@aurora.local",
  "password": "password123"
}
```

## Customers

`GET /customers?page=0&size=10&keyword=&industry=&owner=&riskLevel=`

`POST /customers`

```json
{
  "name": "新客戶股份有限公司",
  "email": "contact@example.com",
  "phone": "0912345678",
  "taxId": "12345678",
  "industry": "SaaS",
  "ownerName": "業務代表",
  "contractStartDate": "2026-01-01",
  "contractEndDate": "2026-12-31",
  "renewalDueDate": "2026-11-30"
}
```

`PUT /customers/{id}/status`

```json
{
  "status": "ACTIVE"
}
```

`POST /customers/{id}/interactions`

```json
{
  "type": "EMAIL",
  "occurredAt": "2026-06-13T10:00:00",
  "content": "客戶要求安排產品續約簡報。"
}
```

## AI

`POST /ai/chat`

```json
{
  "customerId": 1,
  "message": "請分析這個客戶近期風險"
}
```

預設回傳 JSON `ChatResponse`；前端若要使用打字機串流效果，需送出 `Accept: text/event-stream`，同一路徑會回傳 SSE。

## Agent Trace

`GET /agent/customers/{id}/trace`

回傳 Agent steps、風險分數、建議路徑與審核結果。

## Dashboard Reports

`GET /dashboard/reports`

回傳 CRM 經典圖表報表資料：

- `pipelineByStage`：銷售漏斗，各階段商機筆數與金額。
- `monthlyForecast`：依預計成交月份彙總的營收 forecast。
- `industryBreakdown`：依產業彙總的客戶數與商機金額。
- `riskBreakdown`：LOW / MEDIUM / HIGH 客戶風險分布。
- `ownerLeaderboard`：業務排行榜，含客戶數、商機金額、高風險客戶數。
- `renewalForecast`：依續約月份彙總的續約客戶數與商機金額。
- `recentActivities`：近期關鍵互動紀錄。
