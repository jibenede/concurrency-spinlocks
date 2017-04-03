package edu.puc.mecolab.pools.concurrent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of an unbounded total queue. As its name suggests, all operations are non-blocking. When calling
 * {@link UnboundedTotalQueue#take()} and no item is available for consumption, a {@link PoolEmptyException} is thrown.<br>
 * <br>
 * For this implementation, the queue is implemented as a linked list, in which this class only has references to the
 * head and tail of the list. Supports at most one consumer and producer concurrently. By ensuring consumption only
 * affects the head, and production only affects the tail, we enable both operations to be run simultaneously, as long as
 * head != tail.
 */
public class UnboundedTotalQueue<T> implements Pool<T> {
    private ReentrantLock mEnqueueLock;
    private ReentrantLock mDequeueLock;

    private Node<T> mHead;
    private Node<T> mTail;

    public UnboundedTotalQueue() {
        mEnqueueLock = new ReentrantLock();
        mDequeueLock = new ReentrantLock();

        // In its initial state, both head and tail are set to an arbitrary value we call a sentinel. Its value is
        // meaningless and should not be considered as a valid element of the queue, but it does a help with
        // the queue's implementation later. The head always points towards the sentinel, the first valid value of
        // this queue is actually the sentinel's successor!
        mHead = new Node<T>(null);
        mTail = mHead;
    }

    @Override
    public T take() throws PoolEmptyException {
        T result = null;

        mDequeueLock.lock();

        try {
            if (mHead.mNext == null) throw new PoolEmptyException();

            // For removing an item, we grab a reference to the head's successor, then we update the head so it points
            // to its second successor.
            result = mHead.mNext.mValue;
            mHead = mHead.mNext;
        } finally {
            mDequeueLock.unlock();
        }

        return result;
    }

    @Override
    public void put(T element) {
        mEnqueueLock.lock();

        // For adding a new element, we set the current tail's successor as the new element, then we modify the tail
        // so it points to this new element.
        try {
            Node<T> node = new Node<>(element);
            mTail.mNext = node;
            mTail = node;
        } finally {
            mEnqueueLock.unlock();
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
