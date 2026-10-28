#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
星河影城票务平台 · 端到端验证脚本（只依赖标准库）

用法：
    1) 先启动 Redis 与应用（见 README）
    2) python scripts/e2e_test.py [base_url]

覆盖场景：
    座位图三态 / 连座推荐 / 幂等下单 / 并发抢座 / 支付出票 /
    重复售出校验 / 退票手续费 / 改签（补款与退款）/ 排片冲突 / 对账
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
PASS, FAIL = [], []
# 显式禁用代理：本机环境可能存在 HTTP_PROXY 干扰 127.0.0.1 请求
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, body=None):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with OPENER.open(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"code": e.code, "msg": raw or str(e)}


def check(name, cond, detail=""):
    if cond:
        PASS.append(name)
        print("  [PASS] %s %s" % (name, detail))
    else:
        FAIL.append(name)
        print("  [FAIL] %s %s" % (name, detail))


def seatmap(session_id, need=3):
    return call("GET", "/session/%d/seatmap?need=%d" % (session_id, need))


def pick(sm, row_index=4, count=2):
    """从座位图里挑一排连续可售座位，返回 (seatIds, colNos)"""
    row = sm["data"]["rows"][row_index]
    free = [s for s in row["seats"] if s["status"] == 0]
    for i in range(len(free) - count + 1):
        chunk = free[i:i + count]
        if all(chunk[k]["colNo"] + 1 == chunk[k + 1]["colNo"] for k in range(count - 1)):
            return [s["seatId"] for s in chunk], [s["colNo"] for s in chunk]
    raise RuntimeError("找不到连续空位")


def status_of(sm, seat_ids):
    for row in sm["data"]["rows"]:
        for s in row["seats"]:
            if s["seatId"] in seat_ids:
                yield s["status"]


def main():
    print("== 1. 座位图与连座推荐 ==")
    sm = seatmap(1, 3)
    check("座位图返回", sm["code"] == 0 and len(sm["data"]["rows"]) == 10,
          "rows=%d cols=%d" % (len(sm["data"]["rows"]), sm["data"]["colCount"]))
    check("连座推荐非空", len(sm["data"]["recommend"]) > 0, str(sm["data"]["recommend"][:1]))
    rec_cols = sm["data"]["recommend"][0]
    rec_row = sm["data"]["recommendRows"][0]
    rec_seats = [s["seatId"] for s in sm["data"]["rows"][rec_row - 1]["seats"] if s["colNo"] in rec_cols]
    check("推荐座位可定位", len(rec_seats) == 3, "row=%d cols=%s" % (rec_row, rec_cols))
    check("分区价格存在", sm["data"]["rows"][4]["seats"][0].get("price") is not None,
          "price=%s" % sm["data"]["rows"][4]["seats"][0].get("price"))

    print("== 2. 幂等下单 ==")
    m2 = call("POST", "/member", {"phone": "139" + uuid.uuid4().hex[:8].translate(str.maketrans("abcdef", "123456")),
                                  "nickname": "抢座陪跑"})
    member2 = m2["data"]["id"]
    rid = "e2e-" + uuid.uuid4().hex[:8]
    r1 = call("POST", "/order/lock", {"memberId": 1, "sessionId": 1, "seatIds": rec_seats, "requestId": rid})
    check("首次下单成功", r1["code"] == 0, r1.get("msg", ""))
    order_no = r1["data"]["orderNo"] if r1["code"] == 0 else None
    check("订单状态待支付", r1["code"] == 0 and r1["data"]["status"] == 10,
          "payAmount=%s" % (r1["data"]["payAmount"] if order_no else "-"))

    r2 = call("POST", "/order/lock", {"memberId": 1, "sessionId": 1, "seatIds": rec_seats, "requestId": rid})
    check("同 requestId 幂等返回同单", r2["code"] == 0 and r2["data"]["orderNo"] == order_no)

    r3 = call("POST", "/order/lock", {"memberId": member2, "sessionId": 1, "seatIds": rec_seats,
                                      "requestId": "e2e-" + uuid.uuid4().hex[:8]})
    check("他人抢同座位被拒", r3["code"] != 0 and "已被" in r3["msg"], r3["msg"])

    r4 = call("POST", "/order/lock", {"memberId": 1, "sessionId": 1, "seatIds": [sm["data"]["rows"][8]["seats"][0]["seatId"]],
                                      "requestId": "e2e-" + uuid.uuid4().hex[:8]})
    check("同场次未支付订单限制", r4["code"] != 0 and "未支付订单" in r4["msg"], r4["msg"])

    print("== 3. 支付回调与出票 ==")
    pay = call("POST", "/payment/%s/pay" % order_no)
    check("模拟支付成功", pay["code"] == 0, pay.get("msg", ""))
    od = call("GET", "/order/no/%s" % order_no)
    check("订单已出票(30)", od["code"] == 0 and od["data"]["status"] == 30,
          "tickets=%d" % len(od["data"].get("tickets") or []))
    check("票面含取票码", bool(od["data"].get("tickets")) and od["data"]["tickets"][0]["verifyCode"])

    sm2 = seatmap(1, 3)
    check("座位变为已售(2)", all(s == 2 for s in status_of(sm2, rec_seats)), str(list(status_of(sm2, rec_seats))))

    dup = call("POST", "/payment/%s/pay" % order_no)
    check("重复支付回调幂等", dup["code"] == 0, dup.get("msg", ""))

    rec = call("GET", "/order/admin/reconcile")
    check("对账健康", rec["code"] == 0 and rec["data"]["healthy"], str(rec["data"]))

    print("== 4. 退票（阶梯手续费） ==")
    refund = call("POST", "/order/%d/refund?reason=%s" % (od["data"]["id"], urllib.parse.quote("临时有事")))
    check("退票成功", refund["code"] == 0, refund.get("msg", ""))
    if refund["code"] == 0:
        fee_rate = refund["data"]["feeRate"]
        fee = refund["data"]["fee"]
        check("距开场>24h 手续费为0", float(fee_rate) == 0 and float(fee) == 0,
              "feeRate=%s fee=%s refund=%s" % (fee_rate, fee, refund["data"]["refundAmount"]))
    od2 = call("GET", "/order/no/%s" % order_no)
    check("订单已退票(60)", od2["data"]["status"] == 60)

    sm3 = seatmap(1, 3)
    check("退票后座位释放", all(s == 0 for s in status_of(sm3, rec_seats)), str(list(status_of(sm3, rec_seats))))

    print("== 5. 改签（需补款 → 确认） ==")
    seats_a, _ = pick(seatmap(1, 2), 5, 2)
    r5 = call("POST", "/order/lock", {"memberId": 1, "sessionId": 1, "seatIds": seats_a,
                                      "requestId": "e2e-" + uuid.uuid4().hex[:8]})
    check("改签前建单", r5["code"] == 0, r5.get("msg", ""))
    order_a = r5["data"]
    call("POST", "/payment/%s/pay" % order_a["orderNo"])

    imax_seats, _ = pick(seatmap(3, 2), 5, 2)
    ch = call("POST", "/order/change", {"orderId": order_a["id"], "newSessionId": 3, "newSeatIds": imax_seats})
    check("改签需补款", ch["code"] == 0 and ch["data"]["needPay"] is True,
          "diff=%s" % (ch["data"].get("diffAmount") if ch["code"] == 0 else ch.get("msg")))
    if ch["code"] == 0 and ch["data"]["needPay"]:
        confirm = call("POST", "/order/change/%d/pay" % ch["data"]["changeId"])
        check("补款后改签生效", confirm["code"] == 0, confirm.get("msg", ""))
        od3 = call("GET", "/order/%d" % order_a["id"])
        check("订单已改签(70)", od3["data"]["status"] == 70,
              "newSession=%s" % od3["data"]["sessionId"])
        check("新场次座位已售", all(s == 2 for s in status_of(seatmap(3, 2), imax_seats)))
        check("旧座位已释放", all(s == 0 for s in status_of(seatmap(1, 2), seats_a)))

    print("== 6. 排片冲突检测 ==")
    conflict = call("POST", "/session", {"cinemaId": 1, "hallId": 1, "movieId": 1,
                                         "startTime": "2026-10-29 19:40:00", "basePrice": 45.00})
    check("时间重叠被拒", conflict["code"] != 0 and "冲突" in conflict["msg"], conflict["msg"])

    batch = call("POST", "/session/batch", {"sessions": [
        {"cinemaId": 1, "hallId": 2, "movieId": 1, "startTime": "2026-10-30 10:00:00", "basePrice": 60.00},
        {"cinemaId": 1, "hallId": 2, "movieId": 1, "startTime": "2026-10-30 11:00:00", "basePrice": 60.00}
    ]})
    check("批内自检拦住自身冲突", batch["code"] != 0 and "批内自检" in batch["msg"], batch["msg"])

    batch2 = call("POST", "/session/batch", {"sessions": [
        {"cinemaId": 1, "hallId": 2, "movieId": 1, "startTime": "2026-10-30 10:00:00", "basePrice": 60.00},
        {"cinemaId": 1, "hallId": 2, "movieId": 2, "startTime": "2026-10-30 14:00:00", "basePrice": 55.00}
    ]})
    check("合法批量排片成功", batch2["code"] == 0 and len(batch2["data"]) == 2)

    print("== 7. 动态定价 ==")
    preview = call("GET", "/price/preview?sessionId=3")
    check("分区价格不同", preview["code"] == 0 and preview["data"]["B"] > preview["data"]["C"],
          str(preview.get("data")))

    print("== 8. 对账复核 ==")
    rec2 = call("GET", "/order/admin/reconcile")
    check("最终对账健康", rec2["code"] == 0 and rec2["data"]["healthy"], str(rec2["data"]))

    print("\n结果：PASS=%d FAIL=%d" % (len(PASS), len(FAIL)))
    if FAIL:
        print("失败项：" + ", ".join(FAIL))
        sys.exit(1)


if __name__ == "__main__":
    main()
