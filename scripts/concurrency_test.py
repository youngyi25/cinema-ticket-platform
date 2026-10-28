#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
并发抢座验证：N 个会员同时抢同一个座位，必须只有 1 个成功（Lua 原子性的直接证据）。

用法：python scripts/concurrency_test.py [threads] [base_url]
"""
import json
import time
import sys
import threading
import urllib.error
import urllib.request
import uuid

THREADS = int(sys.argv[1]) if len(sys.argv) > 1 else 20
BASE = sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:8080"
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
lock = threading.Lock()
results = []


def call(method, path, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data,
                                 headers={"Content-Type": "application/json"}, method=method)
    try:
        with OPENER.open(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"code": e.code, "msg": raw}


def free_seat(session_id, row_index=9):
    sm = call("GET", "/session/%d/seatmap?need=1" % session_id)["data"]
    for seat in sm["rows"][row_index]["seats"]:
        if seat["status"] == 0:
            return seat["seatId"]
    raise RuntimeError("该排已无空位")


def create_member(i):
    # 手机号需全局唯一：用时间戳尾号 + 序号，避免脚本重复执行时冲突
    phone = "137%06d%02d" % (int(time.time()) % 1000000, i % 100)
    r = call("POST", "/member", {"phone": phone, "nickname": "并发%d" % i})
    if r.get("code") != 0:
        raise RuntimeError("建会员失败: %s" % r.get("msg"))
    return r["data"]["id"]


def main():
    session_id = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    seat_id = free_seat(session_id)
    members = [create_member(i + int(uuid.uuid4().int % 1000) * 0) for i in range(THREADS)]
    print("并发 %d 个会员抢 seatId=%d (session %d)" % (THREADS, seat_id, session_id))

    barrier = threading.Barrier(THREADS)

    def grab(idx):
        barrier.wait()
        r = call("POST", "/order/lock", {
            "memberId": members[idx], "sessionId": session_id,
            "seatIds": [seat_id], "requestId": "cc-" + uuid.uuid4().hex[:10]})
        with lock:
            results.append((idx, r))

    threads = [threading.Thread(target=grab, args=(i,)) for i in range(THREADS)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    ok = [r for _, r in results if r.get("code") == 0]
    rejected = [r for _, r in results if r.get("code") != 0]
    print("成功 %d 单，被拒 %d 单" % (len(ok), len(rejected)))
    for r in rejected[:3]:
        print("  被拒原因示例：%s" % r.get("msg"))

    dup = call("GET", "/order/admin/reconcile")["data"]
    healthy = dup["healthy"] and dup["duplicateSales"] == 0

    if len(ok) == 1 and healthy:
        print("结果：PASS 零重复售出")
    else:
        print("结果：FAIL 成功单数=%d，对账=%s" % (len(ok), dup))
        sys.exit(1)


if __name__ == "__main__":
    main()
