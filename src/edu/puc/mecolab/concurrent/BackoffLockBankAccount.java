package edu.puc.mecolab.concurrent;

import edu.puc.mecolab.Account;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTAS locking implementation refined with a backoff mechanism.
 */
public class BackoffLockBankAccount extends Account {
    private AtomicBoolean mFlag;
    private int mAmount;
    private Backoff mBackoff;

    public BackoffLockBankAccount(int amount) {
        mAmount = amount;
        mFlag = new AtomicBoolean(false);
        mBackoff = new Backoff();
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
        while (true) {
            while (mFlag.get()) {}
            if (!mFlag.getAndSet(true)) {
                return;
            } else {
                mBackoff.backoff();
            }
        }

    }

    private void unlock() {
        mFlag.set(false);
        mBackoff.reset();
    }

    /**
     * Reusable backoff helper class. Do not forget to call {@link Backoff#reset()} when successfully entering the
     * critical section.
     */
    private class Backoff {
        private final int MIN_DELAY = 1;
        private final int MAX_DELAY = 100;

        private int mLimit = MIN_DELAY;
        private final Random mRandom = new Random();

        public void backoff() {
            int delay = mRandom.nextInt(mLimit);
            mLimit = Math.min(MAX_DELAY, mLimit * 2);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void reset() {
            mLimit = MIN_DELAY;
        }
    }
}
