package edu.puc.mecolab.pools.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of a partial bounded concurrent queue.
 * Thread blocking and resuming when conditions are not fulfilled are implement through {@link Condition} objects
 * (which are basically monitors, readers should study its javadoc documentation before studying this code).<br>
 * <br>
 * For this implementation, the queue is implemented as a linked list, in which this class only has references to the
 * head and tail of the list. Supports at most one consumer and producer concurrently. By ensuring consumption only
 * affects the head, and production only affects the tail, we enable both operations to be run simultaneously, as long as
 * head != tail.
 */
public class PartialBoundedQueue<T> implements Pool<T> {
    private ReentrantLock mEnqueueLock;
    private ReentrantLock mDequeueLock;

    private Condition mNotEmptyCondition;
    private Condition mNotFullCondition;

    private AtomicInteger mSize;
    private final int mCapacity;

    private Node<T> mHead;
    private Node<T> mTail;

    public PartialBoundedQueue(int capacity) {
        mCapacity = capacity;

        // In its initial state, both head and tail are set to an arbitrary value we call a sentinel. Its value is
        // meaningless and should not be considered as a valid element of the queue, but it does a help with
        // the queue's implementation later. The head always points towards the sentinel, the first valid value of
        // this queue is actually the sentinel's successor!
        mHead = new Node<>(null);
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
