# -*- coding: utf-8 -*-
"""探针：统计 Knife4j / OpenAPI 文档分组与接口数量，确认新增接口已被收录。"""
import io
import json
import sys
import urllib.parse
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
BASE = "http://127.0.0.1:8080"
_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def get(path):
    # 分组名含中文，必须编码，否则 urllib 在 prepare 阶段会抛 UnicodeEncodeError
    req = urllib.request.Request(BASE + urllib.parse.quote(path, safe="/?=&"),
                                 headers={"Accept": "application/json"})
    with _opener.open(req, timeout=20) as resp:
        return resp.status, resp.read().decode("utf-8", "replace")


for path in ("/doc.html", "/v3/api-docs/swagger-config", "/v3/api-docs"):
    try:
        st, txt = get(path)
        body = txt if "api-docs" not in path else txt[:200].replace("\n", " ")
        print("%-34s HTTP=%s %s" % (path, st, body if path == "/v3/api-docs" else ""))
    except Exception as e:  # noqa: BLE001
        print("%-34s 异常 %s" % (path, e))

st, txt = get("/v3/api-docs/swagger-config")
cfg = json.loads(txt)
print("\n文档分组：")
review_paths = []
for url in cfg.get("urls", []):
    st, doc = get("/" + url["url"].lstrip("/"))
    d = json.loads(doc)
    paths = sorted(d.get("paths", {}).keys())
    print("  %-16s 接口数=%d" % (url["name"], len(paths)))
    review_paths += [p for p in paths if "/review" in p]

print("\n评价相关接口（应出现在 01-APP端接口 与 02-管理端接口 中）：")
for p in sorted(set(review_paths)):
    print("  " + p)
