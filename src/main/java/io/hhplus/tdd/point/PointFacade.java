package io.hhplus.tdd.point;

import io.hhplus.tdd.lock.KeyLock;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class PointFacade {
    private static final Logger log = LoggerFactory.getLogger(PointFacade.class);

    private final KeyLock keyLock;
    private final PointService pointService;

    public PointFacade(KeyLock keyLock, PointService pointService) {
        this.keyLock = keyLock;
        this.pointService = pointService;
    }

    public UserPoint getPoint(long userId) {
        return pointService.getPoint(userId);
    }

    public List<PointHistory> getHistories(long userId) {
        return pointService.getHistories(userId);
    }

    public UserPoint charge(long userId, long amount) {
        String key = "point:" + userId;
        keyLock.lock(key);
        try {
            return pointService.charge(userId, amount);
        } finally {
            keyLock.unlock(key);
        }
    }

    public UserPoint use(long userId, long amount) {
        String key = "point:" + userId;
        long threadId = Thread.currentThread().getId();
        log.info("[스레드 {}] 락 획득 시도 - {}", threadId, key);
        keyLock.lock(key);
        try {
            log.info("[스레드 {}] 락 획득 성공 - {}", threadId, key);
            return pointService.use(userId, amount);
        } finally {
            keyLock.unlock(key);
            log.info("[스레드 {}] 락 해제 - {}", threadId, key);
        }
    }
}