package com.yx.distributed.locks.redis;

import com.yx.distributed.locks.Action;
import com.yx.distributed.locks.DistributedLockManager;
import com.yx.distributed.locks.Lock;
import com.yx.distributed.locks.Scope;
import com.yx.distributed.locks.exception.LockException;
import com.yx.distributed.locks.exception.TimeoutException;
import com.yx.distributed.locks.exception.UnlockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Redis set
 */
@Slf4j
public class RedisSingleNodeLockManager implements DistributedLockManager {
    private static final Duration MinimumLeaseMilliseconds = Duration.ofSeconds(30);
    private static final byte[] RedisLockScript = serialize("" +
            "if (redis.call('setnx', KEYS[1], ARGV[1]) == 1) then " +
            "redis.call('pexpire', KEYS[1], ARGV[2]); " +
            "return 1; " +
            "end; " +
            "return 0;");
    private static final byte[] RedisUnlockScript = serialize("" +
            "if (redis.call('get', KEYS[1]) == ARGV[1]) then " +
            "redis.call('del', KEYS[1]);" +
            "return 1;" +
            "end;" +
            "return 0;");
    private static final byte[] RedisRenewScript = serialize("" +
            "if (redis.call('get', KEYS[1]) == ARGV[1]) then " +
            "redis.call('pexpire', KEYS[1], ARGV[2])" +
            "return 1;" +
            "end;" +
            "return 0;");
    private final String namePrefix;
    private final long renewInterval;
    private final byte[] leaseTimeBytes;
    private final RedisConnectionFactory connectionFactory;
    private final ConcurrentHashMap<String, Locker> lockers;

    public RedisSingleNodeLockManager(String namePrefix, Duration leaseTime, RedisConnectionFactory connectionFactory) {
        this.namePrefix = namePrefix;
        long leaseTimeMills = MinimumLeaseMilliseconds.compareTo(leaseTime) > 0 ? MinimumLeaseMilliseconds.toMillis() : leaseTime.toMillis();
        this.lockers = new ConcurrentHashMap<>();
        this.connectionFactory = connectionFactory;
        this.leaseTimeBytes = serialize(String.valueOf(leaseTimeMills));
        this.renewInterval = leaseTimeMills / 3;
        log.info("namePrefix = {}, leaseTime = {}ms", namePrefix, leaseTimeMills);
        startRenew();
    }

    private static byte[] serialize(String str) {
        return StringRedisSerializer.UTF_8.serialize(str);
    }

    @Override
    public Lock acquire(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("name can not be null or empty");
        }
        name = name + ".lock";
        if (!StringUtils.isEmpty(namePrefix)) {
            name = namePrefix + "." + name;
        }
        Locker locker = new Locker(name, this);
        Locker exists = lockers.putIfAbsent(name, locker);
        return exists != null ? exists : locker;
    }

    @Override
    public void forceUnlock(Lock lock) {
        if (!(lock instanceof Locker)) {
            throw new IllegalArgumentException("unexpected lock instance: " + lock.getClass().getName());
        }
        lockers.remove(lock.getName());
        connectionFactory.getConnection().del(((Locker) lock).nameBytes);
    }

    private void startRenew() {
        Thread thread = new Thread(() -> {
            log.info("start renew with {}ms interval", renewInterval);
            while (true) {
                try {
                    Thread.sleep(renewInterval);
                } catch (InterruptedException e) {
                    log.error("stop renew task,because thread was interrupted", e);
                    return;
                }
                if (lockers.isEmpty()) {
                    continue;
                }
                RedisConnection connection = connectionFactory.getConnection();
                lockers.forEach((name, locker) -> {
                    if (locker.heldThread.get() == 0) {
                        return;
                    }
                    try {
                        log.debug("renew lock {} :", name);
                        connection.eval(RedisRenewScript, ReturnType.INTEGER, 1, locker.nameBytes, locker.token, leaseTimeBytes);
                    } catch (Throwable throwable) {
                        log.error("failed to renew lock", throwable);
                    }
                });
            }
        });
        thread.setName("distributed-lock-renew");
        thread.start();
    }

    private boolean tryUnlockInner(Locker locker) {
        lockers.remove(locker.name);
        RedisConnection connection = connectionFactory.getConnection();
        Long returnVal = connection.eval(RedisUnlockScript, ReturnType.INTEGER, 1, locker.nameBytes, locker.token);
        log.info("tryUnlockInner command response: {}", returnVal);
        return returnVal != null && returnVal == 1;
    }

    private boolean tryLockInner(Locker locker) {
        RedisConnection connection = connectionFactory.getConnection();
        Long returnVal = connection.eval(RedisLockScript, ReturnType.INTEGER, 1, locker.nameBytes, locker.token, leaseTimeBytes);
        if (returnVal != null && returnVal == 1) {
            lockers.put(locker.name, locker);
            return true;
        }
        log.info("tryLockInner command response: {}", returnVal);
        return false;
    }

    @Slf4j
    private static class Locker implements Lock, Scope {
        private final String name;
        private final byte[] nameBytes;
        private final AtomicLong heldThread;
        private final RedisSingleNodeLockManager lockManager;
        private byte[] token;

        private Locker(String name, RedisSingleNodeLockManager lockManager) {
            this.name = name;
            this.lockManager = lockManager;
            this.nameBytes = serialize(name);
            this.heldThread = new AtomicLong();
        }

        private boolean checkReentrant(boolean throwable) {
            if (!heldThread.compareAndSet(0, Thread.currentThread().getId())) {
                if (throwable) {
                    throw new LockException();
                }
                return false;
            }
            this.token = serialize(UUID.randomUUID().toString());
            return true;
        }

        @Override
        public Scope lock() {
            Duration fixedInterval = Duration.ofMinutes(1);
            while (true) {
                if (tryLock(fixedInterval)) {
                    break;
                }
            }
            return this;
        }

        @Override
        public Scope lock(Duration timeout) throws TimeoutException {
            if (tryLock(timeout)) {
                return this;
            }
            throw new TimeoutException();
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public boolean tryLock() {
            if (checkReentrant(false)) {
                try {
                    if (lockManager.tryLockInner(this)) {
                        return true;
                    }
                    heldThread.set(0);
                } catch (Throwable throwable) {
                    heldThread.set(0);
                    log.error("failed to lock", throwable);
                    throw new LockException();
                }
            }
            return false;
        }

        @Override
        public boolean tryLock(Duration timeout) {
            long maxMillis = System.currentTimeMillis() + timeout.toMillis();
            //本地锁竞争
            while (true) {
                //不可重入
                if (isHeldByCurrentThread()) {
                    throw new LockException();
                }

                if (checkReentrant(false)) {
                    break;
                }
                try {
                    if (System.currentTimeMillis() < maxMillis) {
                        Thread.sleep(100);
                    } else {
                        log.info("tryLock timeout,duration: {}ms", timeout.toMillis());
                        return false;
                    }
                } catch (InterruptedException e) {
                    log.error("failed to lock,the thread is interrupted", e);
                    throw new LockException();
                }
            }

            //分布式锁竞争
            try {
                while (true) {
                    if (lockManager.tryLockInner(this)) {
                        return true;
                    }
                    if (System.currentTimeMillis() >= maxMillis) {
                        log.info("tryLock timeout,duration: {}ms", timeout.toMillis());
                        break;
                    }
                    Thread.sleep(100);
                }
                heldThread.set(0);
            } catch (InterruptedException e) {
                heldThread.set(0);
                log.error("failed to lock,the thread is interrupted", e);
                throw new LockException();
            } catch (Throwable throwable) {
                heldThread.set(0);
                log.error("failed to lock", throwable);
            }

            return false;
        }

        @Override
        public void unlock() {
            if (!isHeldByCurrentThread()) {
                log.error("attempt to unlock '{}', not locked by current thread.", getName());
                throw new UnlockException();
            }

            boolean unlocked = false;
            try {
                unlocked = lockManager.tryUnlockInner(this);
            } catch (Throwable throwable) {
                log.error("failed to unlock", throwable);
                throw new UnlockException();
            }

            if (!unlocked) {
                log.error("attempt to unlock '{}', not locked by current thread.", getName());
                throw new UnlockException();
            }

            heldThread.set(0);
        }

        @Override
        public void tryLockWith(Action acquireSuccess, Action acquireFailed) {
            if (tryLock()) {
                try {
                    acquireSuccess.execute();
                } catch (Throwable throwable) {
                    log.error("unexpected exception in 'acquireSuccess'", throwable);
                } finally {
                    unlock();
                }
            } else {
                try {
                    acquireFailed.execute();
                } catch (Throwable throwable) {
                    log.error("unexpected exception in 'acquireFailed'", throwable);
                }
            }
        }

        @Override
        public void tryLockWith(Duration timeout, Action acquireSuccess, Action acquireFailed) {
            if (tryLock(timeout)) {
                try {
                    acquireSuccess.execute();
                } catch (Throwable throwable) {
                    log.error("unexpected exception in 'acquireSuccess'", throwable);
                } finally {
                    unlock();
                }
            } else {
                try {
                    acquireFailed.execute();
                } catch (Throwable throwable) {
                    log.error("unexpected exception in 'acquireFailed'", throwable);
                }
            }
        }

        @Override
        public boolean isHeldByCurrentThread() {
            return heldThread.get() == Thread.currentThread().getId();
        }

        @Override
        public void close() {
            this.unlock();
        }
    }
}