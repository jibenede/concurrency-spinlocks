package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock queueing algorithm that implements a tryLock method. Threads attempting to hold the lock for longer than
 * {@link TimeoutLockBankAccount#THRESHOLD} will be put to sleep for {@link TimeoutLockBankAccount#SLEEP} in order
 * to reduce overhead in the system. This is very useful for situations with high contention, but the THRESHOLD and
 * SLEEP variables must be fine tuned for the problem at hand.
 */
public class TimeoutLockBankAccount extends Account {
    public static final Status AVAILABLE = new Status();

    private static final int THRESHOLD = 10;
    private static final int SLEEP = 100;

    private int mAmount;

    private AtomicReference<Status> mTail;
    private ThreadLocal<Status> mStatus;

    public TimeoutLockBankAccount(int amount) {
        mAmount = amount;

        mTail = new AtomicReference<>(null);

        mStatus = ThreadLocal.withInitial(Status::new);
    }

    public void deposit(int amount) {
        while (!tryLock(THRESHOLD)) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mAmount += amount;
        unlock();
    }

    public void withdraw(int amount) {
        while (!tryLock(THRESHOLD)) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mAmount -= amount;
        unlock();
    }

    public int getAmount() {
        return mAmount;
    }

    private boolean tryLock(int maxWait) {
        long startTime = System.currentTimeMillis();

        Status myStatus = new Status();
        mStatus.set(myStatus);
        myStatus.mPredecessor = null;

        Status myPredecessor = mTail.getAndSet(myStatus);
        if (myPredecessor == null || myPredecessor.mPredecessor == AVAILABLE) return true;

        while (System.currentTimeMillis() - startTime < maxWait) {
            Status mySecondPredecessor = myPredecessor.mPredecessor;
            if (mySecondPredecessor == AVAILABLE) {
                return true;
            } else if (mySecondPredecessor != null) {
                myPredecessor = mySecondPredecessor;
            }
        }

        if (!mTail.compareAndSet(myStatus, myPredecessor)) myStatus.mPredecessor = myPredecessor;

        return false;
    }

    private void unlock() {
        Status myStatus = mStatus.get();
        if (!mTail.compareAndSet(myStatus, null)) myStatus.mPredecessor = AVAILABLE;
    }

    private static class Status {
        private volatile Status mPredecessor = null;
    }
}
