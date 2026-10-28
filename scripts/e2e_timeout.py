#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
超时释放与「支付 / 释放撞车」验证（文档 4.1 / 4.2 / 4.3）

前置：以 1 分钟锁座超时启动应用
    java -jar cinema-app/target/cinema-app.jar --server.port=8080 \
         --cinema.order.lock-timeout-minutes=1

场景：
    A. 未支付 → 到期自动释放（订单 90，座位回到可售）
    B. 先支付 → 释放任务 CAS 影响 0 行，座位保留（订单 30，票存在）
    C. 已释放后再支付 → 回调 CAS 失败 → 立即退款（订单仍 90，产生退款流水）
"""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
PASS, FAIL = [], []
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


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


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print("  [%s] %s %s" % ("PASS" if cond else "FAIL", name, detail))


def seats(session_id, count=1, row_index=6):
    sm = call("GET", "/session/%d/seatmap?need=%d" % (session_id, count))["data"]
    row = sm["rows"][row_index]
    free = [s for s in row["seats"] if s["status"] == 0]
    return [s["seatId"] for s in free[:count]]


def status_of(session_id, seat_ids):
    sm = call("GET", "/session/%d/seatmap?need=1" % session_id)["data"]
    return [s["status"] for row in sm["rows"] for s in row["seats"] if s["seatId"] in seat_ids]


def main():
    print("== A. 未支付订单超时释放 ==")
    a_seats = seats(4, 1)
    a = call("POST", "/order/lock", {"memberId": 1, "sessionId": 4, "seatIds": a_seats,
                                     "requestId": "to-a-" + uuid.uuid4().hex[:6]})
    check("A 建单成功", a["code"] == 0, a.get("msg", ""))
    order_a = a["data"]

    print("== B. 先支付，再等释放任务（CAS 应影响 0 行） ==")
    b_seats = seats(2, 1)
    b = call("POST", "/order/lock", {"memberId": 1, "sessionId": 2, "seatIds": b_seats,
                                     "requestId": "to-b-" + uuid.uuid4().hex[:6]})
    check("B 建单成功", b["code"] == 0, b.get("msg", ""))
    order_b = b["data"]
    pay_b = call("POST", "/payment/%s/pay" % order_b["orderNo"])
    check("B 支付成功", pay_b["code"] == 0, pay_b.get("msg", ""))

    print("== C. 先让它超时，再支付（应触发退款） ==")
    c_seats = seats(5, 1)
    c = call("POST", "/order/lock", {"memberId": 1, "sessionId": 5, "seatIds": c_seats,
                                     "requestId": "to-c-" + uuid.uuid4().hex[:6]})
    check("C 建单成功", c["code"] == 0, c.get("msg", ""))
    order_c = c["data"]

    print("... 等待 75 秒（锁座超时 1 分钟 + 余量）")
    time.sleep(75)

    od_a = call("GET", "/order/%d" % order_a["id"])["data"]
    check("A 订单已取消(90)", od_a["status"] == 90, "status=%s" % od_a["status"])
    check("A 座位已释放", all(s == 0 for s in status_of(4, a_seats)), str(status_of(4, a_seats)))

    od_b = call("GET", "/order/%d" % order_b["id"])["data"]
    check("B 订单仍为已出票(30)", od_b["status"] == 30, "status=%s" % od_b["status"])
    check("B 座位保持已售", all(s == 2 for s in status_of(2, b_seats)), str(status_of(2, b_seats)))
    check("B 票未丢失", len(od_b.get("tickets") or []) == 1)

    od_c = call("GET", "/order/%d" % order_c["id"])["data"]
    check("C 订单已取消(90)", od_c["status"] == 90, "status=%s" % od_c["status"])

    pay_c = call("POST", "/payment/%s/pay" % order_c["orderNo"])
    check("C 支付被拒并提示退款", pay_c["code"] != 0 and "退回" in pay_c.get("msg", ""), pay_c.get("msg", ""))
    refunds = call("GET", "/payment/refund/%s" % order_c["orderNo"])
    check("C 已生成退款流水", refunds["code"] == 0 and len(refunds["data"]) == 1,
          str(refunds["data"][0]["amount"]) if refunds["code"] == 0 and refunds["data"] else "-")
    od_c2 = call("GET", "/order/%d" % order_c["id"])["data"]
    check("C 订单未被改成已支付", od_c2["status"] == 90, "status=%s" % od_c2["status"])

    rec = call("GET", "/order/admin/reconcile")["data"]
    check("对账健康", rec["healthy"], str(rec))

    print("\n结果：PASS=%d FAIL=%d" % (len(PASS), len(FAIL)))
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)


if __name__ == "__main__":
    main()
