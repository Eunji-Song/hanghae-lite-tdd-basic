package io.hhplus.tdd.point;

import io.hhplus.tdd.common.ApiException;
import io.hhplus.tdd.common.ErrorCode;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 단위 테스트 코드 작성
class PointServiceTest {

    private static final Logger log = LoggerFactory.getLogger(PointServiceTest.class);

    private PointService service;

    // 테이블 인스턴스 초기화
    @BeforeEach
    void init() {
        service = new PointService(new UserPointTable(), new PointHistoryTable());
    }

    /**
     * 특성에 따라 그룹화하여 작성
     * - 데이터가 없는 경우
     * - 데이터가 존재하는 경우
     * - 강제로 오류를 발생하는 경우
     */

    @Nested
    @DisplayName("데이터가 존재하지 않는 경우")
    class EmptyCases {


        /**
         * 케이스: 신규 유저 조회 시 0 포인트를 반환한다.
         * 이유: 존재하지 않는 유저의 기본 포인트는 0이어야 함 (초기값 보장).
         */

        @Test
        @DisplayName("GET /point/{id} : 신규 유저는 0 포인트")
        void getPoint_emptyUser_returnsZero() {
            long userId = 1L;

            UserPoint result = service.getPoint(userId);

            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.point()).isZero();
        }


        /**
         * 케이스: 포인트 내역 조회 시 데이터가 없으면 빈 리스트를 반환한다.
         * 이유: 일관된 컨트랙트를 위해 null/예외 대신 빈 리스트 반환.
         */

        @Test
        @DisplayName("GET /point/{id}/histories : 내역 없으면 빈 목록")
        void getHistories_whenEmpty_returnsEmptyList() {
            long userId = 2L;

            List<PointHistory> histories = service.getHistories(userId);

            assertThat(histories).isEmpty();
        }
    }

    @Nested
    @DisplayName("데이터가 존재하는 경우")
    class WithDataCases {

        /**
         * 케이스: 충전 성공 시 잔액이 증가하고 히스토리에 CHARGE가 기록된다.
         * 이유: 요구사항 - 충전 포인트 총액은 누적되고, 기록은 PointHistoryTable에 추가되어야 함.
         */

        @Test
        @DisplayName("PATCH /point/{id}/charge : 충전 성공으로 인한 잔액 증가 + CHARGE 히스토리 기록")
        void charge_increaseBalance_and_historyRecorded() {
            long userId = 3L;

            UserPoint charge = service.charge(userId, 1000);
            assertThat(charge.point()).isEqualTo(1000);

            List<PointHistory> histories = service.getHistories(userId);
            assertThat(histories).hasSize(1);

            PointHistory h = histories.get(0);
            assertThat(h.userId()).isEqualTo(userId);
            assertThat(h.amount()).isEqualTo(1000);
            assertThat(h.type()).isEqualTo(TransactionType.CHARGE);
        }

        /**
         * 케이스: 사용 성공 시 잔액이 감소하고 히스토리에 USE가 기록된다.
         * 이유: 요구사항 - 충전/사용 순서가 보존되어야 하므로 히스토리에 순서가 유지되어야 함.
         */

        @Test
        @DisplayName("PATCH /point/{id}/use : 사용 성공으로 인한 잔액 감소 + USE 히스토리 기록 (순서 보존)")
        void use_decreaseBalance_and_historyRecorded_inOrder() {
            long userId = 4L;

            // 테스트를 위한 충전 진행
            UserPoint charge = service.charge(userId, 1000);
            assertThat(charge.point()).isEqualTo(1000);

            // 사용 진행
            UserPoint after = service.use(userId, 400);
            assertThat(after.point()).isEqualTo(600);

            assertThat(service.getHistories(userId))
                    .extracting(PointHistory::type)
                    .containsExactly(TransactionType.CHARGE, TransactionType.USE);
        }


        /**
         * 케이스: 여러 번 충전 시 잔액이 누적되어 합산된다.
         * 이유: 요구사항 - 충전은 누적 가산되어야 하며 각 충전이 히스토리에 기록되어야 함.
         */

        @Test
        @DisplayName("복수 충전 누적 확인")
        void multiple_charges_accumulate() {
            long userId = 5L;

            UserPoint userPoint = service.charge(userId, 300);
            assertThat(userPoint.point()).isEqualTo(300);

            userPoint = service.charge(userId, 700);
            assertThat(userPoint.point()).isEqualTo(1000);

            assertThat(service.getHistories(userId))
                    .extracting(PointHistory::type)
                    .containsExactly(TransactionType.CHARGE, TransactionType.CHARGE);
        }


    }

    @Nested
    @DisplayName("오류/검증 케이스")
    class ErrorCases {
        /**
         * 케이스: 충전/사용 금액이 0 이하인 경우 예외가 발생해야 한다.
         * 이유: 금액 검증 로직 - 유효하지 않은 값으로 데이터 무결성을 깨지 않도록 차단.
         */

        @Test
        @DisplayName("금액 검증: charge/use 금액은 1 이상 (0/음수 거부)")
        void amount_mustBePositive() {
            long userId = 10L;

            assertThatThrownBy(() -> service.charge(userId, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> service.use(userId, -1))
                    .isInstanceOf(IllegalArgumentException.class);


            // 부적절 요청 이후에도 상태는 변하지 않아야 함
            assertThat(service.getPoint(userId).point()).isZero();
            assertThat(service.getHistories(userId)).isEmpty();
        }

        /**
         * 케이스: 잔액 부족 시 사용 요청은 실패하고 상태는 불변이어야 한다. & 예외 처리 진행 여부에 대한 검증
         * 이유: 일관성 보장 - 실패 시 잔액과 히스토리가 변하면 안 됨.
         */

        @Test
        @DisplayName("PATCH /point/{id}/use : 잔고 부족이면 실패하고 상태는 불변")
        void use_whenInsufficientBalance_fails_and_stateUnchanged() {
            long userId = 11L;

            assertThatThrownBy(() -> service.use(userId, 100))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException ae = (ApiException) ex;
                        assertThat(ae.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
                    })
                    .hasMessageContaining("잔고");


            // 포인트 값은 변경되지 않는다.
            assertThat(service.getPoint(userId).point()).isZero();

            // 기록도 추가되지 않는다.
            assertThat(service.getHistories(userId)).isEmpty();
        }


    }


}