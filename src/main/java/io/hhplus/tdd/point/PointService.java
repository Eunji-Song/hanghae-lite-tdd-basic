package io.hhplus.tdd.point;

import io.hhplus.tdd.common.ApiException;
import io.hhplus.tdd.common.ErrorCode;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /** 특정 유저의 포인트를 조회하는 기능 */
    public UserPoint getPoint(long userId) {
        log.info("getPoint called for userId: {}", userId);
        return userPointTable.selectById(userId);
    }

    /** 특정 유저의 포인트 충전/이용 내역을 조회하는 기능 */
    public List<PointHistory> getHistories(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /** 특정 유저의 포인트를 충전하는 기능 */
    public UserPoint charge(long userId, long amount) {
        log.info("charge called for userId: {}, amount: {}", userId, amount);

        validateAmount(amount);

        UserPoint pointData = userPointTable.selectById(userId);
        long totalPoint = pointData.point() + amount;

        UserPoint balancePoints = userPointTable.insertOrUpdate(userId, totalPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return balancePoints;
    }

    /** 특정 유저의 포인트를 사용하는 기능 */
    public UserPoint use(long userId, long amount) {
        validateAmount(amount);

        UserPoint pointData = userPointTable.selectById(userId);
        if (pointData.point() < amount) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        long totalPoint = pointData.point() - amount;

        if (totalPoint < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        UserPoint balancePoints = userPointTable.insertOrUpdate(userId, totalPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
        return balancePoints;
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT);
        }
    }
}
