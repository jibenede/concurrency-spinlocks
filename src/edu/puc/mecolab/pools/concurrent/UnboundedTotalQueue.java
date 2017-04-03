package edu.puc.mecolab.pools.concurrent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by jose on 4/3/17.
 */
public class UnboundedTotalQueue<T> implements Pool<T> {
    private ReentrantLock mEnqueueLock;
    private ReentrantLock mDequeueLock;

    private volatile Node<T> mHead;
    private volatile Node<T> mTail;

    public UnboundedTotalQueue() {
        mEnqueueLock = new ReentrantLock();
        mDequeueLock = new ReentrantLock();

        mHead = new Node<T>(null);
        mTail = mHead;
    }

    @Override
    public T take() throws PoolEmptyException {
        T result = null;

        mDequeueLock.lock();

        try {
            if (mHead.mNext == null) throw new PoolEmptyException();

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

        try {
            Node<T> node = new Node<T>(element);
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
