package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standard lock queueing algorithm for providing fair, starvation-free, access to the critical section through spinning.
 * Critical section access is managed through a shared array of access flags. Maximum number of flags must be known in
 * advance.
 */
public class QueueLockBankAccount extends Account {
    private static final int CAPACITY = 20;
    private volatile boolean[] mFlags;
    private int mAmount;
    private AtomicInteger mAtomicInteger;

    private ThreadLocal<Integer> mSlot;

    public QueueLockBankAccount(int amount) {
        mAmount = amount;
        mFlags = new boolean[CAPACITY];
        mFlags[0] = true;
        mAtomicInteger = new AtomicInteger(0);

        mSlot = ThreadLocal.withInitial(() -> 0);

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
        // TODO: check for overflowing errors
        int slot = mAtomicInteger.getAndIncrement() % CAPACITY;
        mSlot.set(slot);
        while (!mFlags[slot]) {}
    }

    private void unlock() {
        int slot = mSlot.get();
        mFlags[slot] = false;
        mFlags[(slot + 1) % CAPACITY] = true;
    }
}
