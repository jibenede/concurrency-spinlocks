package edu.puc.mecolab.pools;

import edu.puc.mecolab.pools.concurrent.PartialBoundedQueue;
import edu.puc.mecolab.pools.concurrent.Pool;
import edu.puc.mecolab.pools.concurrent.PoolEmptyException;
import edu.puc.mecolab.pools.concurrent.UnboundedTotalLockFreeQueue;
import edu.puc.mecolab.pools.concurrent.UnboundedTotalQueue;
import edu.puc.mecolab.pools.concurrent.UnsafePool;

import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A standard producer-consumer implementation. Produced objects are expected to be used once only. Make sure the number
 * of produced items is higher than the number of consumed items. In rendezvous implementations, consumers must be
 * equal to producers.
 */
public class ProducerConsumer {
    private static final int NUMBER_OF_PRODUCERS = 5;
    private static final int NUMBER_OF_CONSUMERS = 5;
    private static final int ITERATIONS = 1000000;
    private static final int CAPACITY = 100;

    private enum ExecutorType { CONSUMER, PRODUCER }

    private Semaphore mSemaphore;
    private Pool<ShinyObject> mShinyObjectPool;

    private AtomicInteger mNumberOfNullObjects = new AtomicInteger(0);

    public ProducerConsumer() {

        // mShinyObjectPool = new UnsafePool<ShinyObject>(ShinyObject[].class, CAPACITY);
        // mShinyObjectPool = new PartialBoundedQueue<>(CAPACITY);
        // mShinyObjectPool = new UnboundedTotalQueue<>();
        mShinyObjectPool = new UnboundedTotalLockFreeQueue<>();

        mSemaphore = new Semaphore(- (NUMBER_OF_CONSUMERS + NUMBER_OF_PRODUCERS) + 1);
    }


    public void execute() {
        Executor[] executors = new Executor[NUMBER_OF_PRODUCERS + NUMBER_OF_CONSUMERS];
        for (int i = 0; i < NUMBER_OF_CONSUMERS; i++) {
            executors[i] = new Executor(ExecutorType.CONSUMER, i);
        }
        for (int i = NUMBER_OF_CONSUMERS; i < NUMBER_OF_CONSUMERS + NUMBER_OF_PRODUCERS; i++) {
            executors[i] = new Executor(ExecutorType.PRODUCER, i);
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
        System.out.println("Number of null objects: " + mNumberOfNullObjects.get());
        System.out.println("Program execution time: " + (endTime - startTime));
    }

    private class Executor extends Thread {
        private ExecutorType mExecutorType;
        private Random mRandom;
        private int mSum = 0;

        private Executor(ExecutorType type, int seed) {
            mExecutorType = type;
            mRandom = new Random(seed);
        }

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS;) {
                if (mExecutorType == ExecutorType.CONSUMER) {
                    ShinyObject object = null;
                    try {
                        object = mShinyObjectPool.take();

                        if (object != null) {
                            object.use();
                        } else {
                            // If we get inside here, something somewhere went terribly wrong!
                            mNumberOfNullObjects.incrementAndGet();
                        }
                    } catch (PoolEmptyException e) {
                        // If we have an exception, we retry the operation
                        continue;
                    }


                } else {
                    ShinyObject object = new ShinyObject();
                    mShinyObjectPool.put(object);
                }

                // Uncomment for debugging purposes
                // if (i % 100 == 0) System.out.println(i);

                i++;
            }

            mSemaphore.release();
        }
    }

    private static class ShinyObject {
        private boolean mDirty = false;

        public void use() {
            if (mDirty) System.out.println("WARNING: attempting to use dirty object!");

            mDirty = true;
        }
    }
}
