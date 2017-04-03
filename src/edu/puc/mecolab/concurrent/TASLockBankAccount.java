package edu.puc.mecolab.concurrent;

import edu.puc.mecolab.Account;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standard locking algorithm implemented through test-and-set spinning.
 */
public class TASLockBankAccount extends Account {
    private AtomicBoolean mFlag;
    private int mAmount;

    public TASLockBankAccount(int amount) {
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
        while (mFlag.getAndSet(true)) {}
    }

    private void unlock() {
        mFlag.set(false);
    }
}
