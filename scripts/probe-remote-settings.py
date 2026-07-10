# -*- coding: utf-8 -*-
"""探測遠端 AI 模型設定與供應商是否仍存在於 DB。"""
import json
import sys
import urllib.error
import urllib.request

BASE = "https://aicrm-backend-kt2026.zeabur.app"


def req(method: str, path: str, data=None, token: str | None = None):
    """送出 HTTP JSON 請求，回傳 (status, body)。"""
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = None if data is None else json.dumps(data).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=45) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = raw
        return exc.code, payload
    except Exception as exc:  # noqa: BLE001 — 探測腳本需印出任意連線錯誤
        return -1, str(exc)


def main() -> int:
    print("=== HEALTH ===", flush=True)
    code, health = req("GET", "/api/health")
    print(code, json.dumps(health, ensure_ascii=False, indent=2), flush=True)

    print("\n=== LOGIN admin@aurora.local ===", flush=True)
    code, login = req(
        "POST",
        "/api/auth/login",
        {"username": "admin@aurora.local", "password": "password123"},
    )
    print(code, json.dumps(login, ensure_ascii=False, indent=2)[:1200], flush=True)

    token = None
    if isinstance(login, dict):
        token = login.get("token") or login.get("accessToken")
        if not token and isinstance(login.get("data"), dict):
            token = login["data"].get("token")

    if not token:
        print("LOGIN FAILED — cannot inspect admin settings", flush=True)
        return 1

    print(f"\ntoken acquired (len={len(token)})", flush=True)

    checks = [
        "/api/admin/settings/ai",
        "/api/admin/settings/ai/providers",
        "/api/dashboard/summary",
        "/api/admin/users",
    ]
    for path in checks:
        print(f"\n=== GET {path} ===", flush=True)
        c, d = req("GET", path, token=token)
        print(c, flush=True)
        text = json.dumps(d, ensure_ascii=False, indent=2) if not isinstance(d, str) else d
        print(text[:4000], flush=True)

    # 診斷摘要
    print("\n=== DIAGNOSIS ===", flush=True)
    c, settings = req("GET", "/api/admin/settings/ai", token=token)
    c2, providers = req("GET", "/api/admin/settings/ai/providers", token=token)
    c3, dash = req("GET", "/api/dashboard/summary", token=token)

    if c != 200:
        print(f"settings API broken: HTTP {c}", flush=True)
        return 2

    providers_list = providers if isinstance(providers, list) else (
        settings.get("providers") if isinstance(settings, dict) else []
    )
    if providers_list is None:
        providers_list = []

    model_options = []
    current_model = None
    source = None
    if isinstance(settings, dict):
        current_model = settings.get("currentModel")
        source = settings.get("source")
        model_options = settings.get("modelOptions") or []
        if not providers_list and settings.get("providers"):
            providers_list = settings.get("providers") or []

    print(f"currentModel={current_model!r}", flush=True)
    print(f"source={source!r}", flush=True)
    print(f"modelOptions_count={len(model_options)}", flush=True)
    print(f"providers_count={len(providers_list)}", flush=True)
    if isinstance(dash, dict):
        # 常見摘要欄位
        for k in ("totalCustomers", "customerCount", "customers", "totalOpportunities", "openOpportunities"):
            if k in dash:
                print(f"dashboard.{k}={dash.get(k)}", flush=True)
        print(f"dashboard_keys={list(dash.keys())[:20]}", flush=True)

    if len(providers_list) == 0 and (
        not model_options
        or model_options == [
            {"model": "gemini-3.1-flash-lite-preview", "providerId": None},
            {"model": "gpt-4o-mini", "providerId": None},
        ]
        or all(
            (isinstance(o, dict) and o.get("providerId") in (None, ""))
            or isinstance(o, str)
            for o in model_options
        )
    ):
        print(
            "LIKELY: DB 內為 Flyway 種子／空供應商狀態（自訂模型設定可能已遺失或從未寫入遠端）。",
            flush=True,
        )
    elif len(providers_list) > 0:
        print("OK: 至少有一筆 ai_providers，設定未完全清空。", flush=True)
        for p in providers_list:
            print(f"  provider: {p}", flush=True)
    else:
        print("PARTIAL: 有 modelOptions 但無 providers，請人工對照是否為預期。", flush=True)

    return 0


if __name__ == "__main__":
    sys.exit(main())
