package edu.puc.mecolab.concurrent;

import edu.puc.mecolab.Account;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * A merger of the concepts introduced in the queuing and timeout algorithm implementations. The composite lock
 * consists of a virtual queue of at most {@link CompositeLockBankAccount#SIZE} nodes that are allowed to spin
 * to retrieve the lock, all other threads attempting to get the lock are subject to exponential backoff. If too many
 * back-offs have occurred (over {@link CompositeLockBankAccount#THRESHOLD} in waiting time), the offending thread is put
 * to sleep for {@link CompositeLockBankAccount#SLEEP}.
 *
 * In order to acquire the lock, a thread has to first acquire a {@link QNode}. At that point, it enters the queue and
 * starts spinning, but DOES NOT have the lock yet. Then, it has to wait until the Node reaches the head of the queue.
 * Only then can the lock be considered to be 'acquired'.
 *
 * The virtual queue is represented by the mTail object, a local reference of each thread pointing to the last object
 * of the queue.
 */
public class CompositeLockBankAccount extends Account {
    private static final int THRESHOLD = 500;
    private static final int SLEEP = 1000;

    private static final int SIZE = 4;
    private static final int MIN_BACKOFF = 10;
    private static final int MAX_BACKOFF = 100;

    private int mAmount;

    /**
     * The reference to the last object of the waiting threads' virtual queue. Implemented as an {@link AtomicStampedReference}
     * in order to avoid the ABA problem.
     */
    protected AtomicStampedReference<QNode> mTail;

    /**
     * A list of shared Node objects that allow access to the queue. A thread trying to get the lock gets assigned a
     * node at random. If it is free, it enters the queue, otherwise, it back-offs. QNode's state is handled by an
     * atomic reference, therefore volatile is not needed here.
     */
    private QNode[] mWaiting;

    /**
     * Random object used to retrieve a random node in the node list.
     */
    private Random mRandom;

    /**
     * The currently assigned Node (if any). Once assigned, it means this thread is either waiting for the lock through
     * spinning, or it has control of the lock and is currently accessing its critical section.
     */
    protected ThreadLocal<QNode> mStatus;

    /**
     * Local {@link Backoff} used for this implementation. Simply a minor optimization technique.
     */
    private ThreadLocal<Backoff> mBackoff;

    public CompositeLockBankAccount(int amount) {
        mAmount = amount;

        mTail = new AtomicStampedReference<>(null, 0);
        mWaiting = new QNode[SIZE];
        for (int i = 0; i < mWaiting.length; i++) {
            mWaiting[i] = new QNode();
        }
        mRandom = new Random();
        mBackoff = ThreadLocal.withInitial(Backoff::new);
        mStatus = ThreadLocal.withInitial(() -> null);
    }

    public void deposit(int amount) {
        while (!tryLock(THRESHOLD)) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mAmount += amount;
        unlock();
    }

    public void withdraw(int amount) {
        while (!tryLock(THRESHOLD)) {
            try {
                Thread.sleep(SLEEP);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mAmount -= amount;
        unlock();
    }

    public int getAmount() {
        return mAmount;
    }

    /**
     * Attempts to acquire the lock.
     *
     * @param maxWait The maximum amount of time a thread is allowed to wait before failing with a timeout.
     * @return true if the lock was successfully acquired, false otherwise.
     */
    protected boolean tryLock(int maxWait) {
        long startTime = System.currentTimeMillis();

        Backoff backoff = mBackoff.get();
        try {
            QNode node = acquireNode(backoff, startTime, maxWait);
            QNode predecessor = spliceNode(node, startTime, maxWait);
            waitForPredecessor(predecessor, node, startTime, maxWait);
            backoff.reset();
            return true;
        } catch(TimeoutException e) {
            backoff.reset();
            return false;
        }
    }

    /**
     * Acquires one of the Nodes in the shared list of nodes. Once retrieved, if it is FREE, it is set as WAITING and acquired.
     * If it is ABORTED or RELEASED, queue cleanup is performed. In order to avoid synchronization issues, queue cleanup
     * is only allowed to be done by one thread: the last one at the queue. This is arbitrary, another condition could
     * be used to achieve the same result. After the cleanup, a CAS is attempted to obtain the node.
     * If the node is WAITING or if after the cleanup the CAS fails, the thread backs-off.
     *
     * @param backoff The backoff object that implements exponential backoff.
     * @param startTime The time this thread started trying to acquire the lock.
     * @param maxWait The maximum amount of time a thread is allowed to backoff before failing with a timeout.
     * @return A QNode in an 'acquired' state.
     * @throws TimeoutException If the thread fails to acquire the node before maxWait has elapsed.
     */
    private QNode acquireNode(Backoff backoff, long startTime, int maxWait) throws TimeoutException {
        QNode node = mWaiting[mRandom.nextInt(SIZE)];
        QNode currentTail;

        while(true) {
            if (node.mState.compareAndSet(State.FREE, State.WAITING)) {
                return node;
            }

            int[] stamp = new int[1];
            currentTail = mTail.get(stamp);
            State state = node.mState.get();

            if (state == State.ABORTED || state == State.RELEASED) {
                // Condition check to make sure only one thread cleans up the node
                if (node == currentTail) {
                    // Cleanup: if the state is aborted and this is the last node in the queue, we must change the tail
                    // shared global variable so it points to the aborted node's predecessor.
                    // If the state is released, that means it has just been set as such by its predecessor, who has
                    // just quit its critical section. There is no need to enqueue then, we can just acquire the node
                    // immediately, then the lock and we can set the queue as empty by setting its tail to null.
                    QNode predecessor = null;
                    if (state == State.ABORTED) {
                        predecessor = node.mPredecessor;
                    }
                    if (mTail.compareAndSet(currentTail, predecessor, stamp[0], stamp[0] + 1)) {
                        node.mState.set(State.WAITING);
                        return node;
                    }
                }
            }
            backoff.backoff();
            if (System.currentTimeMillis() - startTime > maxWait) throw new TimeoutException();
        }
    }

    /**
     * Splices node into virtual queue of threads waiting for the lock. This is done atomically through CAS in order
     * to prevent synchronization issues. Timeout condition is checked between failed CAS.
     *
     * @param node The 'acquired' node that is being tried to splice into the virtual queue of WAITING threads.
     * @param startTime The time this thread started trying to acquire the lock.
     * @param maxWait The maximum amount of time a thread is allowed to backoff before failing with a timeout.
     * @return The predecessor of the node in the virtual node queue. At this point, the node has been inserted into the
     * queue, so the returned item corresponds to the before-to-last node in the queue.
     * @throws TimeoutException If the thread fails to splice the node into the queue before maxWait has elapsed.
     */
    private QNode spliceNode(QNode node, long startTime, int maxWait) throws TimeoutException {
        QNode currentTail;
        int[] stamp;

        do {
            stamp = new int[1];
            currentTail = mTail.get(stamp);
            if (System.currentTimeMillis() - startTime > maxWait) {
                node.mState.set(State.FREE);
                throw new TimeoutException();
            }
        } while (!mTail.compareAndSet(currentTail, node, stamp[0], stamp[0] + 1));

        return currentTail;
    }

    /**
     * Spin waits for the predecessors in the virtual node queue to complete before acquiring the lock.
     * Once this method returns without an exception, the thread can consider the lock to be 'acquired'.
     *
     * @param predecessor This thread's acquired node's predecessor.
     * @param node This thread's acquired node.
     * @param startTime The time this thread started trying to acquire the lock.
     * @param maxWait The maximum amount of time a thread is allowed to backoff before failing with a timeout.
     * @throws TimeoutException If the thread fails to acquire the lock before maxWait has elapsed.
     */
    private void waitForPredecessor(QNode predecessor, QNode node, long startTime, int maxWait) throws TimeoutException {
        if (predecessor == null) {
            mStatus.set(node);
            return;
        }

        State predecessorState = predecessor.mState.get();
        while(predecessorState != State.RELEASED) {
            if (predecessorState == State.ABORTED) {
                QNode temp = predecessor;
                predecessor = predecessor.mPredecessor;
                temp.mState.set(State.FREE);
            }

            if (System.currentTimeMillis() - startTime > maxWait) {
                node.mPredecessor = predecessor;
                node.mState.set(State.ABORTED);
                throw new TimeoutException();
            }

            predecessorState = predecessor.mState.get();
        }
        predecessor.mState.set(State.FREE);
        mStatus.set(node);
    }

    /**
     * Releases the lock.
     */
    protected void unlock() {
        QNode QNode = mStatus.get();
        QNode.mState.set(State.RELEASED);
        mStatus.set(null);
    }

    private class Backoff {
        private int mLimit = MIN_BACKOFF;
        private final Random mRandom = new Random();

        public void backoff() {
            int delay = mRandom.nextInt(mLimit);
            mLimit = Math.min(MAX_BACKOFF, mLimit * 2);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void reset() {
            mLimit = MIN_BACKOFF;
        }
    }

    protected static class QNode {
        private AtomicReference<State> mState;
        private QNode mPredecessor;

        public QNode() {
            mState = new AtomicReference<>(State.FREE);
        }
    }

    private enum State {
        /**
         * Node's initial state, free for acquiring. A node may be set to FREE back from WAITING only if it is the
         * tail of the queue. A node may be set FREE from ABORTED once proper cleanup has been performed. A node
         * may be set FREE from RELEASED once its predecessor has acquired the lock.
         */
        FREE,
        /**
         * Node that has been acquired, the thread that acquired it is currently spinning to wait for the lock.
         */
        WAITING,
        /**
         * Node whose thread has just quit its critical section. It is now free for acquiring by the previous node
         * in the queue. If no other node is in the queue, should be marked as FREE.
         */
        RELEASED,
        /**
         * Enqueued node in which its timeout has transpired. Successor's predecessor in the queue should be updated
         * to this node's predecessor to maintain consistency. After cleanup, should be marked as FREE.
         */
        ABORTED
    }
}
