package com.xinghe.cinema.common.redis;

/**
 * Lua 脚本集中定义。脚本内容与文档 2.2 / 4.4 一致。
 *
 * <p>要点：两轮循环，先全查再全写；脚本内只做位运算，不查库、不遍历大集合。</p>
 */
public final class LuaScripts {

    private LuaScripts() {
    }

    /**
     * 原子锁座。
     * KEYS[1] = seat:occ:{sessionId}
     * ARGV[1] = 逗号分隔的 offset 列表，如 "42,43,44"
     * 返回 0 = 全部锁定成功；1 = 存在已占座位，未做任何修改
     */
    public static final String LOCK_SEATS = """
            local raw = ARGV[1]
            local offsets = {}
            for s in string.gmatch(raw, '([^,]+)') do
                offsets[#offsets + 1] = tonumber(s)
            end
            if #offsets == 0 then
                return 2
            end
            for i = 1, #offsets do
                if redis.call('GETBIT', KEYS[1], offsets[i]) == 1 then
                    return 1
                end
            end
            for i = 1, #offsets do
                redis.call('SETBIT', KEYS[1], offsets[i], 1)
            end
            return 0
            """;

    /**
     * 原子批量清位（文档 4.4）。避免"解锁到一半，别的请求看到部分座位可用"。
     * KEYS[1] = seat:occ:{sessionId}
     * ARGV[1] = 逗号分隔的 offset 列表
     * 返回清理的位数
     */
    public static final String RELEASE_SEATS = """
            local raw = ARGV[1]
            local offsets = {}
            for s in string.gmatch(raw, '([^,]+)') do
                offsets[#offsets + 1] = tonumber(s)
            end
            for i = 1, #offsets do
                redis.call('SETBIT', KEYS[1], offsets[i], 0)
            end
            return #offsets
            """;

    /**
     * 无条件置位，仅用于 bitmap 重建（文档 4.5，Redis 故障恢复）。
     * KEYS[1] = seat:occ:{sessionId}
     * ARGV[1] = 逗号分隔的 offset 列表
     */
    public static final String MARK_OCCUPIED = """
            local raw = ARGV[1]
            local count = 0
            for s in string.gmatch(raw, '([^,]+)') do
                redis.call('SETBIT', KEYS[1], tonumber(s), 1)
                count = count + 1
            end
            return count
            """;
}
