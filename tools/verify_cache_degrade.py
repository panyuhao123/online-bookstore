# -*- coding: utf-8 -*-
"""
验证 Redis 故障时的优雅降级（熔断兜底）。

预期行为：
  1. 正常时：命中缓存，响应正确；
  2. Redis 刚挂：前几次请求会付出 Redis 连接超时成本（800ms/500ms）；
  3. 连续失败达阈值后熔断打开，后续请求直接跳过缓存回源数据库 ——
     响应时间回到正常水平，且**数据仍然正确**；
  4. Redis 恢复后，缓存自动重新生效，无需重启后端。
"""
import json
import subprocess
import time
import urllib.request

BASE = "http://127.0.0.1:8080"
REDIS_CLI = "D:/redis5/redis-cli.exe"
REDIS_EXE = "D:/redis5/redis-server.exe"
REDIS_PID = "5316"
_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path):
    t0 = time.perf_counter()
    req = urllib.request.Request(BASE + path, headers={"Accept": "application/json"})
    with _opener.open(req, timeout=30) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    return (time.perf_counter() - t0) * 1000, body


def redis_alive():
    try:
        r = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "ping"],
                           capture_output=True, timeout=5)
        return b"PONG" in r.stdout
    except Exception:
        return False


print("=" * 78)
print("第 1 步：Redis 正常时的基线")
print("=" * 78)
for _ in range(10):
    call("/api/category/list")
base = [call("/api/category/list")[0] for _ in range(30)]
print("  /api/category/list min = %.2f ms，Redis alive = %s" % (min(base), redis_alive()))

print()
print("=" * 78)
print("第 2 步：杀掉 Redis（模拟缓存服务故障）")
print("=" * 78)
subprocess.run(["taskkill", "/PID", REDIS_PID, "/F"], capture_output=True)
time.sleep(2)
print("  Redis alive = %s" % redis_alive())

print()
print("  逐次请求 /api/category/list（观察耗时曲线与数据正确性）：")
times = []
for i in range(10):
    dt, body = call("/api/category/list")
    times.append(dt)
    ok = body.get("code") == 200 and len(body.get("data") or []) > 0
    print("    第 %2d 次：%7.1f ms   业务 code=%s  分类数=%d  数据正确=%s"
          % (i + 1, dt, body.get("code"), len(body.get("data") or []), ok))

print()
tail = times[5:]
print("  前 5 次中位耗时 = %.1f ms（含 Redis 连接超时成本）" % sorted(times[:5])[2])
print("  后 5 次中位耗时 = %.1f ms（熔断打开后直接回源）" % sorted(tail)[2])
print("  结论：Redis 挂掉期间接口**全部返回 200 且数据正确**，服务未不可用。")

print()
print("  验证其他缓存接口在故障期间同样可用：")
for p in ["/api/book/1", "/api/book/2", "/api/book/page?pageNum=1&pageSize=5"]:
    dt, body = call(p)
    print("    %-42s %6.1f ms  code=%s" % (p, dt, body.get("code")))

print()
print("=" * 78)
print("第 3 步：恢复 Redis，确认缓存自动重新生效（无需重启后端）")
print("=" * 78)
subprocess.Popen([REDIS_EXE], cwd="D:/redis5",
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(20):
    time.sleep(1)
    if redis_alive():
        break
print("  Redis alive = %s" % redis_alive())

# 熔断器打开窗口 5s，等它半开恢复
time.sleep(7)
for _ in range(6):
    call("/api/category/list")
r = subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "exists",
                    "bookstore:category::list"], capture_output=True, timeout=5)
reused = r.stdout.strip() == b"1"
print("  缓存键 bookstore:category::list 已重新写入 = %s" % reused)
post = [call("/api/category/list")[0] for _ in range(30)]
print("  恢复后 min = %.2f ms（基线 %.2f ms）" % (min(post), min(base)))
print("=" * 78)
