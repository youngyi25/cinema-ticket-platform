package com.xinghe.cinema.common.redis;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 座位 bitmap 的 Redis 访问层（唯一允许读写 seat:occ:* 的地方）。
 *
 * <p>三态设计（文档 1.3）：bitmap 只表达「不可售」，谁占的看 MySQL。</p>
 */
@Component
public class SeatBitmapRedis {

    private static final DefaultRedisScript<Long> LOCK_SEATS =
            new DefaultRedisScript<>(LuaScripts.LOCK_SEATS, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SEATS =
            new DefaultRedisScript<>(LuaScripts.RELEASE_SEATS, Long.class);

    private static final DefaultRedisScript<Long> MARK_OCCUPIED =
            new DefaultRedisScript<>(LuaScripts.MARK_OCCUPIED, Long.class);

    private final StringRedisTemplate redis;

    public SeatBitmapRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 原子锁座：全部成功或全部不改。
     *
     * @return true = 锁定成功；false = 存在已占座位（未做任何修改）
     */
    public boolean lockSeats(long sessionId, List<Integer> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return false;
        }
        String key = RedisKeys.seatOcc(sessionId);
        String arg = offsets.stream().map(String::valueOf).collect(Collectors.joining(","));
        Long r = redis.execute(LOCK_SEATS, List.of(key), arg);
        return r != null && r == 0L;
    }

    /** 原子批量清位（释放座位）。 */
    public long releaseSeats(long sessionId, List<Integer> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return 0L;
        }
        String key = RedisKeys.seatOcc(sessionId);
        String arg = offsets.stream().map(String::valueOf).collect(Collectors.joining(","));
        Long r = redis.execute(RELEASE_SEATS, List.of(key), arg);
        return r == null ? 0L : r;
    }

    /** 单个位置位，用于 bitmap 重建。 */
    public void setBit(long sessionId, int offset, boolean occupied) {
        redis.opsForValue().setBit(RedisKeys.seatOcc(sessionId), offset, occupied);
    }

    /** 批量置位（重建时一次脚本写完，避免 N 次往返）。 */
    public long markOccupied(long sessionId, List<Integer> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return 0L;
        }
        String arg = offsets.stream().map(String::valueOf).collect(Collectors.joining(","));
        Long r = redis.execute(MARK_OCCUPIED, List.of(RedisKeys.seatOcc(sessionId)), arg);
        return r == null ? 0L : r;
    }

    /** 单个位查询。仅在退化路径使用（正常走整场 GETRANGE）。 */
    public boolean isOccupied(long sessionId, int offset) {
        Boolean b = redis.opsForValue().getBit(RedisKeys.seatOcc(sessionId), offset);
        return Boolean.TRUE.equals(b);
    }

    /**
     * 一次取回整场 bitmap（文档 3.1），避免逐座 GETBIT 造成 N 次网络往返。
     *
     * @return 原始字节，key 不存在时返回 null
     */
    public byte[] loadRaw(long sessionId) {
        String key = RedisKeys.seatOcc(sessionId);
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        return redis.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(k));
    }

    /** 删除整场 bitmap（重建前清空）。 */
    public void delete(long sessionId) {
        redis.delete(RedisKeys.seatOcc(sessionId));
    }
}
