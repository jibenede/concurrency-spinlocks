package edu.puc.mecolab.spinlocks.concurrent;

import edu.puc.mecolab.spinlocks.Account;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A benchmark of the Atomic* class of objects. Very fast, but limited to very specific implementations (most of the
 * time the critical section involves handling multiple variables).
 */
public class AtomicBankAccount extends Account {
    private AtomicInteger mAmount;

    public AtomicBankAccount(int amount) {
        mAmount = new AtomicInteger(amount);
    }

    public void deposit(int amount) {
        mAmount.addAndGet(amount);
    }

    public void withdraw(int amount) {
        mAmount.addAndGet(- amount);
    }

    public int getAmount() {
        return mAmount.get();
    }
}
