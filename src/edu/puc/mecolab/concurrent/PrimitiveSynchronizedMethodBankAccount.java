package edu.puc.mecolab.concurrent;

import edu.puc.mecolab.Account;

/**
 * Standard locking using synchronized methods. Should be marginally worse than synchronized block locking.
 */
public class PrimitiveSynchronizedMethodBankAccount extends Account {
    private int mAmount;

    public PrimitiveSynchronizedMethodBankAccount(int amount) {
        mAmount = amount;
    }

    synchronized public void deposit(int amount) {
        mAmount += amount;
    }

    synchronized public void withdraw(int amount) {
        mAmount -= amount;
    }

    public int getAmount() {
        return mAmount;
    }
}
