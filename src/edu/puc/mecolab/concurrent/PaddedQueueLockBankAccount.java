package edu.puc.mecolab.concurrent;

import edu.puc.mecolab.Account;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Similar implementation to {@link QueueLockBankAccount} but with an added padding between flags in the shared array
 * to prevent the phenomenon of "false cache invalidation".
 */
public class PaddedQueueLockBankAccount extends Account {
    private static final int CAPACITY = 20;
    private static final int STEP = 8;
    private static final int ACTUAL_CAPACITY = CAPACITY * STEP;

    private int mAmount;

    // Shared variables used for spinning, must be declared volatile
    private volatile boolean[] mFlags;

    private AtomicInteger mAtomicInteger;
    private ThreadLocal<Integer> mSlot;

    public PaddedQueueLockBankAccount(int amount) {
        mAmount = amount;
        mFlags = new boolean[ACTUAL_CAPACITY];
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
        int slot =  mAtomicInteger.getAndAdd(STEP) % (ACTUAL_CAPACITY);
        mSlot.set(slot);
        while (!mFlags[slot]) {}
    }

    private void unlock() {
        int slot = mSlot.get();
        mFlags[slot] = false;
        mFlags[(slot + STEP) % (ACTUAL_CAPACITY)] = true;
    }
}
