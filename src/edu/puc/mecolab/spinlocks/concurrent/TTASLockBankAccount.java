package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /**
 * Standard locking algorithm implemented through test-test-and-set spinning (or compare-swap). Minor improvement over
 * its TAS equivalent.
 */
public class TTASLockBankAccount extends Account {
    private AtomicBoolean mFlag;
    private int mAmount;

    public TTASLockBankAccount(int amount) {
        mAmount = amount;
        mFlag = new AtomicBoolean(false);
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
            // The following is equivalent to this block of code, but more efficient.
            // With this, you can notice a minor improvement over TAS Locking.
            //
            // <code>
            // while (mFlag.get()) {}
            // if (!mFlag.getAndSet(true)) return;
            // </code>

            while (!mFlag.compareAndSet(false, true)) {}
            return;
        }

    }

    private void unlock() {
        mFlag.set(false);
    }
}
