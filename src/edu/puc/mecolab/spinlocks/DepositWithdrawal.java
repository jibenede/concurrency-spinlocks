package edu.puc.mecolab.spinlocks;

import java.util.Random;
import java.util.concurrent.Semaphore;

import edu.puc.mecolab.spinlocks.concurrent.AtomicBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.BackoffLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.CLHLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.CompositeLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.FastPathCompositeLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.MonitorBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.PaddedQueueLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.PrimitiveLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.PrimitiveSynchronizedBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.QueueLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.TASLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.TTASLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.TimeoutLockBankAccount;
import edu.puc.mecolab.spinlocks.concurrent.UnsafeBankAccount;

/**
 * Created by jose on 2/23/17.
 */
public class DepositWithdrawal {
    private static final int ITERATIONS = 1000000;
    private static final int INITIAL_AMOUNT = 1000000;
    private static final int NUM_THREADS = 10;

    private Account mBankAccount;
    private Semaphore mSemaphore;

    public DepositWithdrawal() {
        // Non-thread safe, just to show a baseline
        // ---
        // mBankAccount = new UnsafeBankAccount(INITIAL_AMOUNT);

        // Implementation through primitives provided by the JVM. Usually these are better than anything we can come up with.
        // ---
        // mBankAccount = new MonitorBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new PrimitiveLockBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new PrimitiveSynchronizedBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new AtomicBankAccount(INITIAL_AMOUNT);

        // Illustration of the spin lock concept
        // ---
        // mBankAccount = new TASLockBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new TTASLockBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new BackoffLockBankAccount(INITIAL_AMOUNT);

        // WARNING: for queue locks, if NUM_THREADS > VIRTUAL CORES, they become extremely slow
        // ---
        // mBankAccount = new QueueLockBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new PaddedQueueLockBankAccount(INITIAL_AMOUNT);
        // mBankAccount = new CLHLockBankAccount(INITIAL_AMOUNT);

        // Queue locks that sleep after failing to obtain the lock before a timeout elapses
        // ---
        // mBankAccount = new TimeoutLockBankAccount(INITIAL_AMOUNT);

        // CompositeLock (high difficulty! this will be on the exam (¬‿¬) )
        // ---
        // mBankAccount = new CompositeLockBankAccount(INITIAL_AMOUNT);
        mBankAccount = new FastPathCompositeLockBankAccount(INITIAL_AMOUNT);

        mSemaphore = new Semaphore(- NUM_THREADS + 1);

    }

    public void execute() {
        Executor[] executors = new Executor[NUM_THREADS];
        for (int i = 0; i < executors.length; i++) {
            executors[i] = new Executor(i);
        }

        long startTime = System.currentTimeMillis();

        for (Thread thread : executors) {
            thread.start();
        }

        try {
            mSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();

        int total = mBankAccount.getAmount();
        for (Executor executor : executors) {
            total += executor.mSum;
        }

        System.out.println("Total: " + total);
        System.out.println("Program execution time: " + (endTime - startTime));
    }

    private class Executor extends Thread {
        private Random mRandom;
        private int mSum = 0;

        private Executor(int seed) {
            mRandom = new Random(seed);
        }

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; i++) {
                int choice = mRandom.nextInt(2);
                int amount = mRandom.nextInt(1000);
                if (choice == 0) {
                    mBankAccount.withdraw(amount);
                    mSum += amount;
                } else {
                    mBankAccount.deposit(amount);
                    mSum -= amount;
                }

                // Uncomment for debugging purposes
                // if (i % 100 == 0) System.out.println(i);
            }

            mSemaphore.release();
        }
    }
}
