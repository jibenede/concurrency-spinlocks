package edu.puc.mecolab.spinlocks;

/**
 * Created by jose on 2/23/17.
 */
public abstract class Account {
    public abstract void deposit(int amount);

    public abstract void withdraw(int amount);

    public abstract int getAmount();
}
