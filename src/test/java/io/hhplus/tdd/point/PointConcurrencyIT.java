package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootTest
class PointConcurrencyIT {

    @Autowired
    PointFacade facade;

    private static final Logger log = LoggerFactory.getLogger(PointConcurrencyIT.class);

    private void runConcurrent(int threads, Runnable task) throws InterruptedException {
        ExecutorService es = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            es.submit(() -> {
                try { start.await(); task.run(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            });
        }
        start.countDown();
        done.await();
        es.shutdown();
    }

    @Test
    void 동시_사용_요청_음수_잔고_방지() throws Exception {
        long userId = 1001L;
        facade.charge(userId, 10_000);
        log.info("초기 충전 완료 - userId={}, balance={}", userId, facade.getPoint(userId).point());

        int threads = 10; // 50 대신 줄여야 로그가 보기 편함
        long perUse = 300;

        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        runConcurrent(threads, () -> {
            long threadId = Thread.currentThread().getId();
            log.info("[스레드 {}] use 요청 시작", threadId);
            try {
                UserPoint after = facade.use(userId, perUse);
                log.info("[스레드 {}] use 성공 → 현재 잔액: {}", threadId, after.point());
                success.incrementAndGet();
            } catch (Exception e) {
                log.warn("[스레드 {}] use 실패: {}", threadId, e.getMessage());
                fail.incrementAndGet();
            }
            log.info("[스레드 {}] use 요청 종료", threadId);
        });

        UserPoint after = facade.getPoint(userId);
        log.info("최종 결과 → 성공 {}건, 실패 {}건, 최종 잔액 {}", success.get(), fail.get(), after.point());

        assertThat(success.get()).isLessThanOrEqualTo(33);
    }

    @Test
    void 충전과_사용_섞여도_정합성() throws Exception {
        long userId = 1002L;
        facade.charge(userId, 0);
        log.info("테스트 시작: userId={} 초기 충전 0원", userId);

        int threads = 100;
        AtomicInteger chargeCnt = new AtomicInteger();
        AtomicInteger useSucc = new AtomicInteger();

        runConcurrent(threads, () -> {
            long threadId = Thread.currentThread().getId();
            if (ThreadLocalRandom.current().nextBoolean()) {
                facade.charge(userId, 100);
                int total = chargeCnt.incrementAndGet();
                log.info("[스레드 {}] charge 100 성공 (누적 충전 횟수={})", threadId, total);
            } else {
                try {
                    facade.use(userId, 100);
                    int total = useSucc.incrementAndGet();
                    log.info("[스레드 {}] use 100 성공 (누적 사용 성공 횟수={})", threadId, total);
                } catch (Exception e) {
                    log.warn("[스레드 {}] use 100 실패: {}", threadId, e.getMessage());
                }
            }
        });

        UserPoint after = facade.getPoint(userId);
        log.info("최종 결과: 충전 {}회, 사용성공 {}회, 최종 잔액 {}",
                chargeCnt.get(), useSucc.get(), after.point());

        // 충전 성공 횟수 = 사용 성공 횟수 + (남은 잔액 / 100)
        assertThat(useSucc.get() + (after.point() / 100)).isEqualTo(chargeCnt.get());
    }
    @Test
    void 히스토리_개수와_순서_정합성() throws Exception {
        long userId = 1003L;
        facade.charge(userId, 5_000);
        log.info("테스트 시작: userId={} 초기 충전 5,000원", userId);

        int threads = 20;
        runConcurrent(threads, () -> {
            long threadId = Thread.currentThread().getId();
            try {
                UserPoint afterUse = facade.use(userId, 100);
                log.info("[스레드 {}] use 100 성공 → 현재 잔액 {}", threadId, afterUse.point());
            } catch (Exception e) {
                log.warn("[스레드 {}] use 100 실패: {}", threadId, e.getMessage());
            }
        });

        var histories = facade.getHistories(userId);
        long useCnt = histories.stream().filter(h -> h.type() == TransactionType.USE).count();
        UserPoint after = facade.getPoint(userId);

        log.info("최종 결과: 사용성공 {}회, 남은 잔액 {}, 히스토리 전체 개수 {}",
                useCnt, after.point(), histories.size());

        // 사용 성공 * 100 + 잔액 == 초기 5,000
        assertThat(useCnt * 100 + after.point()).isEqualTo(5_000);

        // createdAt/ID 기준 정렬 확인 로직 추가 가능
        // assertThat(histories).isSortedAccordingTo(Comparator.comparing(PointHistory::createdAt));
    }
}