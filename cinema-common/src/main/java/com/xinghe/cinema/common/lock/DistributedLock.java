package com.xinghe.cinema.common.lock;

/**
 * 分布式锁抽象。生产实现为 Redisson（与文档 5.3 一致），
 * 便于在无 Redis 环境下替换为本地锁实现做单测。
 */
public interface DistributedLock {

    /**
     * 尝试加锁。
     *
     * @param key           锁 key
     * @param waitSeconds   最长等待秒数
     * @param leaseSeconds  持锁租约秒数，防止节点宕机死锁
     * @return 是否加锁成功
     */
    boolean tryLock(String key, long waitSeconds, long leaseSeconds);

    /** 释放锁，未持有则忽略。 */
    void unlock(String key);
}
