package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

/**
 * Standard locking using synchronized blocks.
 */
public class PrimitiveSynchronizedBankAccount extends Account {
    private static final Object sLock = new Object();
    private int mAmount;

    public PrimitiveSynchronizedBankAccount(int amount) {
        mAmount = amount;
    }

    public void deposit(int amount) {
        synchronized (sLock) {
            mAmount += amount;
        }
    }

    public void withdraw(int amount) {
        synchronized (sLock) {
            mAmount -= amount;
        }
    }

    public int getAmount() {
        return mAmount;
    }

}
