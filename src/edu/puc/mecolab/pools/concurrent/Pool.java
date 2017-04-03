package edu.puc.mecolab.pools.concurrent;

/**
 * Created by jose on 4/3/17.
 */
public interface Pool<T> {
    T take();
    void put(T element);
}
