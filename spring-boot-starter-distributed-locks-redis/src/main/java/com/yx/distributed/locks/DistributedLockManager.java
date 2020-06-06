package com.yx.distributed.locks;

public interface DistributedLockManager {
    /**
     * 通过给定的{@code name}获取锁
     * <ol>
     *     <li>{@code name} 用于标识锁的名称</li>
     *     <li>相同{@code name}获取的锁实例是相同的</li>
     * </ol>
     *
     * @param name 锁的名称
     * @return
     */
    Lock acquire(String name);

    /**
     * 强制删除锁
     * 该方法容易引起锁状态的异常，所以一般情况下应尽可能的使用{@link Lock#unlock()}
     * 只有在因为网络等异常原因导致锁已经处于异常状态时，才应该考虑使用该方法立刻删除锁
     * 目标锁被强制删除后，任何已经持有该锁的Owner在解锁时都将触发异常
     *
     * @return
     */
    void forceUnlock(Lock lock);
}