package edu.puc.mecolab.pools.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronized concurrent queue that incorporates a rendezvous behavior. SLOW AS HELL!!!<br>
 * <br>
 * In order to ensure a coordinated access to the shared resources, access to the critical section is controlled
 * through a lock object. For both queueing and dequeueing, only one thread is allowed. As such, this implementation
 * has very little support for actual concurrency, therefore it is quite slow.
 */
public class SynchronizedQueue<T> implements Pool<T> {
    /**
     * Shared item set for consumption. Once set, a consumer must take it in order for the program to move forward.
     */
    private T mItem;
    /**
     * This shared variable is set to true when a producer is in the process of queueing an item. Successive producers
     * must wait for the current producer's item to be consumed before entering the critical section.
     */
    private boolean mEnqueueing;
    private Lock mLock;
    private Condition mCondition;

    public SynchronizedQueue() {
        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
    }

    @Override
    public T take() throws PoolEmptyException {
        mLock.lock();

        try {
            // If no item has been put for consumption, we must wait for a producer to do so.
            while (mItem == null) mCondition.await();

            // We unset the item placed for consumption. This is inside the critical section, so no further synchronization
            // is required.
            T temp = mItem;
            mItem = null;

            // Other consumers or producers might have been put on hold, we must wake them up.
            mCondition.signalAll();

            return temp;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mLock.unlock();
        }

        // If we get here, something BAD happened.
        return null;
    }

    @Override
    public void put(T element) {
        mLock.lock();

        try {
            // If another producer is waiting for a consumer, the current producer must be put on hold.
            while (mEnqueueing)  mCondition.await();

            // We switch this flag to indicate we are now waiting for a consumer and an item is available for consumption.
            mEnqueueing = true;
            mItem = element;

            // We signal consumers put on hold to wake up. Producers are also affected but at this stage they are irrelevant.
            mCondition.signalAll();

            // We must now wait for the item to be consumed by someone else before proceeding.
            while (mItem != null) mCondition.await();

            // Item has been consumed, we must change the flag to inform others about it, while also signalling other
            // producers and consumers to wake up.
            mEnqueueing = false;
            mCondition.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mLock.unlock();
        }

    }
}
