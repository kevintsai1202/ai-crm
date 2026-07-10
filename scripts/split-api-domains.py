# -*- coding: utf-8 -*-
"""將 api/rest.ts 再拆成 domain 檔（auth/customers/dashboard/ai/manager/admin/workspace）。"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
rest_path = root / "frontend" / "src" / "api" / "rest.ts"
text = rest_path.read_text(encoding="utf-8")

# 去掉 header，只留函式
header_end = text.index("export async function fetchHealth")
header = text[:header_end]
body = text[header_end:]

# 以 export async function / export type / export function 切段
parts = re.split(r"(?=^export (?:async function|function|type) )", body, flags=re.M)

domains = {
    "auth": [],
    "customers": [],
    "dashboard": [],
    "ai": [],
    "manager": [],
    "admin": [],
    "workspace": [],
    "common": [],
}

def classify(name: str) -> str:
    n = name.lower()
    if any(k in n for k in ("login", "logout", "health")):
        return "auth"
    if any(k in n for k in ("workspace",)):
        return "workspace"
    if any(k in n for k in ("admin", "provider", "aisetting", "modeltest", "modelscore", "logmodel")):
        return "admin"
    if any(k in n for k in ("team", "owner", "manager", "portfolio", "agenttrace")):
        # portfolio/agent often AI but under manager dashboard flows — keep portfolio in ai
        if "portfolio" in n or "agent" in n:
            return "ai"
        return "manager"
    if any(k in n for k in ("dashboard", "drilldown", "rfm", "sentiment", "demodata", "layout")):
        return "dashboard"
    if any(k in n for k in ("customer", "contact", "opportunity", "interaction")):
        return "customers"
    if any(k in n for k in ("assistant", "assessment", "aifeedback", "aius", "chat", "sse", "stream", "citation")):
        return "ai"
    if n in ("ssechunk", "readssestream", "handlestreamunauthorized"):
        return "ai"
    return "ai"

for p in parts:
    if not p.strip():
        continue
    m = re.match(r"export (?:async function|function|type) (\w+)", p)
    name = m.group(1) if m else "unknown"
    domains[classify(name)].append(p)

# SSE helpers must be before streams — they're type/function without export async at start sometimes
# readSseStream is not exported - it's function readSseStream
# re-scan for unexported helpers
helpers = re.findall(
    r"(?:/\*\*[\s\S]*?\*/\s*)?(?:async )?function (readSseStream|handleStreamUnauthorized)[\s\S]*?(?=\n(?:export |/\*\*|async function |function |\Z))",
    body,
)
# Better attach full helper blocks from original body
helper_blocks = []
for hm in re.finditer(
    r"/\*\*[\s\S]*?\*/\s*(?:async )?function (readSseStream|handleStreamUnauthorized)\([\s\S]*?\n\}",
    body,
):
    helper_blocks.append(hm.group(0))

# Also SseChunk type
sse_type = re.search(r"/\*\* SSE[\s\S]*?export type SseChunk[\s\S]*?;\n", body)
sse_block = sse_type.group(0) if sse_type else "export type SseChunk = { type: string; delta?: string; citations?: any[]; risk?: any; callId?: number; items?: any[] };\n"

api_dir = root / "frontend" / "src" / "api"
shared_imports = '''import type {
  AdminUser,
  AgentTraceResponse,
  AiCallHistoryItem,
  ChatMessageHistoryItem,
  ChatResponse,
  ContactResponse,
  CustomerDetail,
  CustomerOptionsResponse,
  CustomerSummary,
  DashboardLayoutResponse,
  DashboardReports,
  DashboardSummary,
  HealthResponse,
  InteractionResponse,
  LoginResponse,
  ManagerAnalyticsResponse,
  ManagerInsightResponse,
  OpportunityResponse,
  PageResponse,
  Role,
  PortfolioAssessment,
  DrilldownResponse,
  RfmResponse,
  SentimentRadarResponse,
  UsageSummaryResponse
} from "../types";
import { apiClient, getAuthHeaders, AI_TIMEOUT } from "./client";

'''

# Rebuild ai.ts with helpers first
ai_parts = domains["ai"]
# Filter out broken splits of helpers
ai_clean = [p for p in ai_parts if "function readSseStream" not in p and "function handleStreamUnauthorized" not in p]
ai_content = shared_imports + sse_block + "\n\n".join(helper_blocks) + "\n\n" + "".join(ai_clean)

def write_domain(name: str, chunks: list[str]):
    if not chunks and name != "ai":
        return
    if name == "ai":
        content = ai_content
    else:
        content = shared_imports + "".join(chunks)
    (api_dir / f"{name}.ts").write_text(content, encoding="utf-8")
    print(name, "bytes", len(content))

for k, v in domains.items():
    if k == "common":
        continue
    write_domain(k, v)

index = '''/** API 模組入口：依 domain 拆檔，維持 from \"../api\" 相容。 */
export { TOKEN_KEY, apiClient, getAuthHeaders, AI_TIMEOUT } from "./client";
export * from "./auth";
export * from "./customers";
export * from "./dashboard";
export * from "./ai";
export * from "./manager";
export * from "./admin";
export * from "./workspace";
'''
(api_dir / "index.ts").write_text(index, encoding="utf-8")
rest_path.unlink()
print("removed rest.ts")
