# -*- coding: utf-8 -*-
import json
import urllib.error
import urllib.request

payload = json.dumps({"username": "sales@aurora.local", "password": "password123"}).encode()

for url in (
    "http://127.0.0.1:18080/api/auth/login",
    "http://127.0.0.1:5173/api/auth/login",
):
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            print(url, "OK", resp.status, body[:400])
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(url, "HTTP", e.code, body[:800])
    except Exception as e:
        print(url, "ERR", type(e).__name__, e)
