# -*- coding: utf-8 -*-
"""
严谨量化 Redis 缓存收益。

方法学：
  - HTTP 往返本身有 5~30ms 噪声，单次对比毫无意义；改用「多次采样取最小值」，
    min 是真实服务耗时的最佳估计（过滤掉 GC、调度、网络抖动）。
  - 冷调用 = 每次请求前先 DEL 掉缓存键，强制回源数据库。
  - 热调用 = 让缓存自然命中。
"""
import json
import subprocess
import time
import urllib.request

BASE = "http://127.0.0.1:8080"
REDIS_CLI = "D:/redis5/redis-cli.exe"
_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

ROUNDS = 60


def del_key(key):
    subprocess.run([REDIS_CLI, "-h", "127.0.0.1", "-p", "6379", "del", key],
                   capture_output=True)


def call(path):
    t0 = time.perf_counter()
    req = urllib.request.Request(BASE + path, headers={"Accept": "application/json"})
    with _opener.open(req, timeout=20) as resp:
        resp.read()
    return (time.perf_counter() - t0) * 1000


def bench(label, path, key, rounds=ROUNDS):
    # 预热（JIT、连接池），避免把冷启动成本算进去
    for _ in range(10):
        call(path)

    cold = []
    for _ in range(rounds):
        del_key(key)
        cold.append(call(path))

    for _ in range(10):
        call(path)
    warm = []
    for _ in range(rounds):
        warm.append(call(path))

    c, w = min(cold), min(warm)
    gain = (c - w) / c * 100 if c else 0
    print("%-26s 冷(回源) min %6.2f ms | 热(命中) min %6.2f ms | 提速 %5.1f%%"
          % (label, c, w, gain))
    return c, w


print("=" * 84)
print("Redis 缓存收益（min 采样 %d 次，冷调用前 DEL 强制回源）" % ROUNDS)
print("=" * 84)
bench("分类列表 /category/list", "/api/category/list", "bookstore:category::list")
bench("图书详情 /book/1", "/api/book/1", "bookstore:book::1")
bench("图书详情 /book/2", "/api/book/2", "bookstore:book::2")

print()
print("对照：未缓存的筛选分页 /book/page（同样采样，无缓存可命中）")
p = "/api/book/page?pageNum=1&pageSize=10&minPrice=30&maxPrice=60"
for _ in range(10):
    call(p)
samples = [call(p) for _ in range(ROUNDS)]
print("%-26s min %6.2f ms（无缓存优化，作为基线参照）" % ("分页筛选", min(samples)))
print("=" * 84)
