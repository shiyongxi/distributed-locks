package com.yx.distributed.locks;

import com.yx.distributed.locks.exception.LockException;
import com.yx.distributed.locks.exception.TimeoutException;
import com.yx.distributed.locks.exception.UnlockException;

import java.time.Duration;

public interface Lock {
    /**
     * 加锁
     * 若无法立即获得锁，当前线程将阻塞，直到锁可用
     * 若发生异常，会抛出 {@link LockException}
     * 可查看日志确定详细原因
     *
     * @return
     */
    Scope lock();

    /**
     * 在指定的{@code timeout}时间内加锁
     * 若发生异常，会抛出 {@link LockException}
     * 可查看日志确定详细原因
     *
     * @param timeout 超时时间
     * @return
     * @throws TimeoutException 若在指定的超时时间内未能加锁成功，抛出
     */
    Scope lock(Duration timeout) throws TimeoutException;

    /**
     * 获取当前锁的名称
     *
     * @return
     */
    String getName();

    /**
     * 尝试加锁
     * 该方法会尝试进行加锁，无论加锁是否成功，都回立即返回，不会阻塞
     * 若发生异常，会抛出 {@link LockException}
     * 可查看日志确定详细原因
     *
     * @return 若加锁成功返回 true，失败返回 false
     */
    boolean tryLock();

    /**
     * 用于确定锁是否由当前线程持有
     *
     * @return 是返回 true，否返回 false
     */
    boolean isHeldByCurrentThread();

    /**
     * 尝试在指定的{@code timeout}时间内进行加锁
     * 在指定时间内无法完成加锁操作时，会立即返回
     *
     * @param timeout 超时时间
     * @return 若加锁成功返回 true，失败返回 false
     */
    boolean tryLock(Duration timeout);

    /**
     * 解锁
     * 若发生异常，会抛出 {@link UnlockException}
     * 可查看日志确定详细原因
     */
    void unlock();

    /**
     * 尝试加锁，并在成功或者失败时执行对应的方法
     * <ol>
     *     <li>若加锁成功，则执行{@code acquireSuccess}</li>
     *     <li>若加锁失败，则执行{@code acquireFailed}</li>
     *     <li>无论加锁是否成功，最后都会自动释放锁</li>
     *     <li>加锁过程中发生非预期异常，会抛出{@link LockException}</li>
     * </ol>
     * 注意：
     * <ol>
     *     <li>只有与锁相关的异常才会抛出</li>
     *     <li>而参数中提供的方法{@code acquireSuccess}或{@code acquireFailed}若引发异常，会记录Error级别的日志但不会被抛出</li>
     * </ol>
     * Usage: 尝试加锁，并在加锁成功时打印"ok"，加锁失败时打印"failed"
     * <code>
     *     tryLockWith(() -> System.out.println("ok"), () -> System.out.println("failed"));
     * </code>
     *
     * @param acquireSuccess 加锁成功后执行
     * @param acquireFailed  任何原因导致的加锁失败后执行
     */
    void tryLockWith(Action acquireSuccess, Action acquireFailed);

    /**
     * 尝试在指定的{@code timeout}时间内进行加锁，并在成功或者失败时执行对应的方法
     * <ol>
     *     <li>若加锁成功，则执行{@code acquireSuccess}</li>
     *     <li>若加锁失败，则执行{@code acquireFailed}</li>
     *     <li>无论加锁是否成功，最后都会自动释放锁</li>
     *     <li>加锁过程中发生非预期异常，会抛出{@link LockException}</li>
     * </ol>
     * 注意：
     * <ol>
     *     <li>只有与锁相关的异常才会抛出</li>
     *     <li>而参数中提供的方法{@code acquireSuccess}或{@code acquireFailed}若引发异常，会记录Error级别的日志但不会被抛出</li>
     * </ol>
     * Usage: 尝试在1秒内加锁，并在加锁成功时打印"ok"，加锁失败时打印"failed"
     * <code>
     *     tryLockWith(Duration.ofSeconds(1), () -> System.out.println("ok"), () -> System.out.println("failed"));
     * </code>
     *
     * @param timeout        超时时间
     * @param acquireSuccess 加锁成功后执行
     * @param acquireFailed  任何原因导致的加锁失败后执行
     */
    void tryLockWith(Duration timeout, Action acquireSuccess, Action acquireFailed);
}