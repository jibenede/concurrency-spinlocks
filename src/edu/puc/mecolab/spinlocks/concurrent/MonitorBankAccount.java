package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

/**
 * Standard locking using monitors using Java's wait/notify mechanism. Should perform worse under multi-core architectures
 * than synchronized locks.
 */
public class MonitorBankAccount extends Account {
    private static final Object mMonitor = new Object();
    private boolean mFlag;
    private int mAmount;

    public MonitorBankAccount(int amount) {
        mAmount = amount;
        mFlag = false;
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
        synchronized (mMonitor) {
            while (mFlag) {
                try {
                    mMonitor.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mFlag = true;
        }
    }

    private void unlock() {
        synchronized (mMonitor) {
            mFlag = false;
            mMonitor.notify();
        }
    }
}
