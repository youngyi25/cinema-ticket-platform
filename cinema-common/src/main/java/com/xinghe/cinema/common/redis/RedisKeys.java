package com.xinghe.cinema.common.redis;

/**
 * Redis key 规范。key 命名与文档保持一致。
 *
 * <pre>
 * seat:occ:{sessionId}                  座位占用 bitmap（bit=1 不可售）
 * idem:order:{memberId}:{requestId}     下单幂等键
 * lock:schedule:hall:{hallId}           排片分布式锁
 * </pre>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 座位占用 bitmap，唯一事实来源（文档 1.1）。 */
    public static String seatOcc(long sessionId) {
        return "seat:occ:" + sessionId;
    }

    /** 下单幂等键（文档 2.4）。 */
    public static String idemOrder(long memberId, String requestId) {
        return "idem:order:" + memberId + ":" + requestId;
    }

    /** 排片锁（文档 5.3）。 */
    public static String scheduleHallLock(long hallId) {
        return "lock:schedule:hall:" + hallId;
    }
}
