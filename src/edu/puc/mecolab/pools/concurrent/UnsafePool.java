package edu.puc.mecolab.pools.concurrent;

import java.lang.reflect.Array;

/**
 * A thread-unsafe pool implementation that does everything except what it's supposed to do.
 */
public class UnsafePool<T> implements Pool<T> {
    private T[] mElements;

    private int mFirst;
    private int mLast;

    public UnsafePool(Class<T[]> clazz, int capacity) {
        mElements =  clazz.cast(Array.newInstance(clazz.getComponentType(), capacity));
        mFirst = mLast = 0;
    }

    @Override
    public T take() {
        if (size() > 0) {
            T result = mElements[mFirst];
            mFirst = (mFirst + 1) % mElements.length;

            return result;
        } else {
            return null;
        }
    }

    @Override
    public void put(T element) {
        if (size() < mElements.length - 1) {
            mElements[mLast] = element;
            mLast = (mLast + 1) % mElements.length;
        }
    }

    public int size() {
        int size = mLast - mFirst;
        if (size < 0) size += mElements.length;
        return size;
    }
}
