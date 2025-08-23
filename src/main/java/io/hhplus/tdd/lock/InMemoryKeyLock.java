package io.hhplus.tdd.lock;

import java.util.concurrent.locks.ReentrantLock;

public class InMemoryKeyLock implements KeyLock {
    private final ReentrantLock[] stripes;

    public InMemoryKeyLock(int stripesCount, boolean fair) {
        this.stripes = new ReentrantLock[stripesCount];
        for (int i = 0; i < stripesCount; i++) stripes[i] = new ReentrantLock(fair);
    }

    private ReentrantLock stripe(String key) {
        int idx = Math.abs(key.hashCode() % stripes.length);
        return stripes[idx];
    }

    @Override public void lock(String key)   { stripe(key).lock(); }
    @Override public void unlock(String key) { stripe(key).unlock(); }
}