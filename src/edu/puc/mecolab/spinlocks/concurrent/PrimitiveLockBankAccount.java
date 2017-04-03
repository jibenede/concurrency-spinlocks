package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Experiment using the standard {@link ReentrantLock} class for locking. Reentrant locks have been demonstrated
 * to perform better under high contention scenarios than synchronized blocks.
 *
 * @see <a href=https://mechanical-sympathy.blogspot.cl/2011/11/java-lock-implementations.html>Mechanical Sympathy Blog</a>
 */
public class PrimitiveLockBankAccount extends Account {
    private ReentrantLock mLock;
    private int mAmount;

    public PrimitiveLockBankAccount(int amount) {
        mAmount = amount;
        mLock = new ReentrantLock();
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
        mLock.lock();
    }

    private void unlock() {
        mLock.unlock();
    }
}
