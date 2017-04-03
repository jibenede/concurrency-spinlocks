package edu.puc.mecolab.concurrent;

/**
 * A composite lock optimized to perform better in uncontended scenarios. When retrieving the lock, if no node is found
 * at the tail, we assume an uncontended scenario. We mark this situation by placing a high order bit on the node's stamp.
 *
 * In this case, we avoid all the overhead of node claiming, splicing and queue cleanup. Otherwise, we fall back to the
 * original composite lock implementation.
 */
public class FastPathCompositeLockBankAccount extends CompositeLockBankAccount {
    private static final int  FASTPATH = 1 << 30; // 0b01000000000000000000000000000000

    public FastPathCompositeLockBankAccount(int amount) {
        super(amount);
    }

    private boolean fastPathLock() {
        int[] stamp = {0};
        QNode node = mTail.get(stamp);
        int oldStamp = stamp[0];

        // If there are queued nodes, we are in a contended scenario and cannot use fast path.
        if (node != null) {
            return false;
        }

        // If the tail's stamp has the FASTPATH bit set, someone else is using fast path and we cannot continue.
        // May happen if 2 or more threads attempt fast path simultaneously.
        if ((oldStamp & FASTPATH) != 0) {
            return false;
        }

        int newStamp = (oldStamp + 1) | FASTPATH;

        // Do note fast path does not enqueue anything, the reference remains null, we just change the stamp.
        return mTail.compareAndSet(node, null, oldStamp, newStamp);
    }

    private boolean fastPathUnlock() {
        int oldStamp = mTail.getStamp();
        // If the tail's stamp has not the FASTPATH bit set, this is not a fast path lock and we must fall back to the
        // regular unlock.
        if ((oldStamp & FASTPATH) == 0) {
            return false;
        }

        // We simply remove the FASTPATH bit from the stamp (it will be preserved by all nodes enqueueing themselves).
        // There should be no way for the CAS to fail.
        int[] stamp = {0};
        int newStamp;
        QNode node;
        do {
            node = mTail.get(stamp);
            oldStamp = stamp[0];
            newStamp = oldStamp & (~FASTPATH);
        } while (!mTail.compareAndSet(node, node, oldStamp, newStamp));

        return true;
    }

    @Override
    protected boolean tryLock(int maxWait) {
        if (fastPathLock()) {
            return true;
        }

        if (super.tryLock(maxWait)) {
            // If a previous thread used fast lock, this thread needs to first wait for it to finish its work.
            // We do so in this loop.
            // TODO: timeout if previous thread's critical section duration exceeds maxWait.
            while ((mTail.getStamp() & FASTPATH) != 0) {};
            return true;
        }

        return false;
    }

    @Override
    protected void unlock() {
        if (!fastPathUnlock()) {
            super.unlock();
        }
    }
}
