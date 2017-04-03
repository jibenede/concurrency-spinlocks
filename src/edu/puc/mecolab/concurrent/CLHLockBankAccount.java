package edu.puc.mecolab.concurrent;

import edu.puc.mecolab.Account;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock queueing algorithm similar to {@link QueueLockBankAccount}, implemented through a linked list, in which the
 * tail of the queue is globally visible through a shared variable and each thread maintains a reference to its
 * previous member's flag in the queue (its predecessor). If the flag is set to true, the lock is being held.
 * Successors then spin on the flag until it is set to false through unlocking.
 */
public class CLHLockBankAccount extends Account {
    private static final int CAPACITY = 20;

    private int mAmount;

    private AtomicReference<Status> mTail;
    private ThreadLocal<Status> mMyLockStatus;
    private ThreadLocal<Status> mPredecessorLockStatus;

    public CLHLockBankAccount(int amount) {
        mAmount = amount;

        mTail = new AtomicReference<>(new Status());

        mMyLockStatus = ThreadLocal.withInitial(Status::new);

        mPredecessorLockStatus = new ThreadLocal<>(); // Value initialized to null
    }

    public void deposit(int amount) {
        lock();
        mAmount += amount;
        unlock();
    }

    public void withdraw(int amount) {
        lock();
        mAmount -= amount;
        unlock();
    }

    public int getAmount() {
        return mAmount;
    }

    private void lock() {
        Status status = mMyLockStatus.get();
        status.mLocked = true;

        Status predecessor = mTail.getAndSet(status);
        mPredecessorLockStatus.set(predecessor);
        while (predecessor.mLocked) {}
    }

    private void unlock() {
        Status status = mMyLockStatus.get();
        status.mLocked = false;

        mMyLockStatus.set(mPredecessorLockStatus.get());
    }

    private static class Status {
        // Shared variable between at most 2 threads used for spinning. Must be declared volatile.
        private volatile boolean mLocked;
    }
}
