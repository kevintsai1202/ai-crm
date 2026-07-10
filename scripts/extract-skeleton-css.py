from pathlib import Path
p = Path(r"d:/GitHub/ai-crm/frontend/src/styles.css")
t = p.read_text(encoding="utf-8")
marker = "/* 客戶詳情等區塊 skeleton */"
i = t.find(marker)
if i < 0:
    raise SystemExit("marker not found")
j = t.find(".skeleton-list {", i)
if j < 0:
    raise SystemExit("skeleton-list not found")
sk = t[i:j]
styles = Path(r"d:/GitHub/ai-crm/frontend/src/styles")
styles.mkdir(exist_ok=True)
(styles / "skeleton.css").write_text(sk, encoding="utf-8")
p.write_text('@import "./styles/skeleton.css";\n\n' + t[:i] + t[j:], encoding="utf-8")
print("ok", len(sk))
