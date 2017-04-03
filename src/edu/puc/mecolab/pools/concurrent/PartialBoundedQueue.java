package edu.puc.mecolab.pools.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of a partial bounded concurrent queue. Supports at most one consumer and producer concurrently.
 * Thread blocking and resuming when conditions are not fulfilled are implement through {@link Condition} objects
 * (which are basically monitors, readers should study its javadoc documentation before studying this code).
 */
public class PartialBoundedQueue<T> implements Pool<T> {
    private ReentrantLock mEnqueueLock;
    private ReentrantLock mDequeueLock;

    private Condition mNotEmptyCondition;
    private Condition mNotFullCondition;

    private AtomicInteger mSize;
    private final int mCapacity;

    private volatile Node<T> mHead;
    private volatile Node<T> mTail;

    public PartialBoundedQueue(int capacity) {
        mCapacity = capacity;
        mHead = new Node<T>(null);
        mTail = mHead;

        mSize = new AtomicInteger(0);

        mEnqueueLock = new ReentrantLock();
        mNotFullCondition = mEnqueueLock.newCondition();

        mDequeueLock = new ReentrantLock();
        mNotEmptyCondition = mDequeueLock.newCondition();
    }

    @Override
    public T take() {
        T result = null;

        boolean wakeEnqueuers = false;

        mDequeueLock.lock();
        try {
            while (mSize.get() == 0) mNotEmptyCondition.await();

            // Note here that the first element in this implementation is the value of the head's successor. That
            // is because when the queue is empty, there is one head and tail element with null values.
            result = mHead.mNext.mValue;
            mHead = mHead.mNext;

            if (mSize.getAndDecrement() == mCapacity) wakeEnqueuers = true;

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mDequeueLock.unlock();
        }

        if (wakeEnqueuers) {
            mEnqueueLock.lock();

            mNotFullCondition.signalAll();

            mEnqueueLock.unlock();
        }

        // Result is not null.
        return result;
    }

    @Override
    public void put(T element) {
        boolean wakeDequeuers = false;

        mEnqueueLock.lock();
        try {
            while (mSize.get() == mCapacity) mNotFullCondition.await();

            Node<T> node = new Node<T>(element);
            mTail.mNext = node;
            mTail = node;

            if (mSize.getAndIncrement() == 0) wakeDequeuers = true;

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mEnqueueLock.unlock();
        }

        if (wakeDequeuers) {
            mDequeueLock.lock();

            mNotEmptyCondition.signalAll();

            mDequeueLock.unlock();
        }
    }

    private static class Node<T> {
        public T mValue;
        public volatile Node<T> mNext;

        public Node(T element) {
            mValue = element;
            mNext = null;
        }
    }
}
