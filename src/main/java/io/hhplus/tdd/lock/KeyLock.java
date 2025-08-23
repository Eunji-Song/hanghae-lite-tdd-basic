package io.hhplus.tdd.lock;

public interface KeyLock {
    void lock(String key);
    void unlock(String key);
}