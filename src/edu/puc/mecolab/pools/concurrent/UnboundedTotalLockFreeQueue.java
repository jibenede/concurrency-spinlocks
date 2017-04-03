package edu.puc.mecolab.pools.concurrent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by jose on 4/3/17.
 */
public class UnboundedTotalLockFreeQueue<T> implements Pool<T> {

    private volatile AtomicReference<Node<T>> mHead;
    private volatile AtomicReference<Node<T>> mTail;

    public UnboundedTotalLockFreeQueue() {
        Node<T> sentinel = new Node<T>(null);
        mHead = new AtomicReference<>();
        mHead.set(sentinel);
        mTail = new AtomicReference<>();
        mTail.set(sentinel);
    }

    @Override
    public T take() throws PoolEmptyException {
        while (true) {
            Node<T> first = mHead.get();
            Node<T> last = mTail.get();
            Node<T> next = first.mNext.get();

            if (first == mHead.get()) {
                if (first == last) {
                    if (next == null) {
                        throw new PoolEmptyException();
                    }
                    mTail.compareAndSet(last, next);
                } else {
                    T value = next.mValue;
                    if (mHead.compareAndSet(first, next)) return value;
                }
            }
        }
    }

    @Override
    public void put(T element) {
        Node<T> node = new Node<>(element);
        while (true) {
            Node<T> last = mTail.get();
            Node<T> next = last.mNext.get();
            if (last == mTail.get()) {
                if (next == null) {
                    if (last.mNext.compareAndSet(next, node)) {
                        mTail.compareAndSet(last, node);
                        return;
                    }
                } else {
                    mTail.compareAndSet(last, next);
                }
            }
        }
    }

    private static class Node<T> {
        public volatile T mValue;
        public AtomicReference<Node<T>> mNext;

        public Node(T element) {
            mValue = element;
            mNext = new AtomicReference<>(null);
        }
    }
}
