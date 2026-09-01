package com.chalkak.point.service;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.point.controller.response.PointResponse;
import com.chalkak.point.entity.Point;
import com.chalkak.point.repository.PointRepository;
import com.chalkak.user.entity.User;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final PointRepository pointRepository;
    private final UserRepository userRepository;

    public PointResponse findByUserId(Long userId) {
        return pointRepository.findByUserId(userId)
            .map(PointResponse::from)
            .orElseGet(() -> PointResponse.empty(userId));
    }

    @Transactional
    public PointResponse charge(Long userId, BigDecimal amount) {
        Point point = pointRepository.findByUserIdWithLock(userId)
            .orElseGet(() -> createPoint(userId));
        point.charge(amount);
        return PointResponse.from(point);
    }

    @Transactional
    public PointResponse lock(Long userId, BigDecimal amount) {
        Point point = getPointWithLock(userId);
        point.lock(amount);
        return PointResponse.from(point);
    }

    @Transactional
    public PointResponse unlock(Long userId, BigDecimal amount) {
        Point point = getPointWithLock(userId);
        point.unlock(amount);
        return PointResponse.from(point);
    }

    @Transactional
    public void settlePoint(Long newBidderId, Long previousBidderId, BigDecimal newBidAmount, BigDecimal previousBidAmount) {
        // 두 사용자의 포인트 행을 잠그는 순서가 호출마다(누가 새 입찰자인지에 따라) 달라지면
        // 교차 입찰 상황에서 서로 다른 순서로 잠그다 데드락이 발생할 수 있어, userId 오름차순으로 순서를 고정
        Long firstUserId = Math.min(newBidderId, previousBidderId);
        Long secondUserId = Math.max(newBidderId, previousBidderId);

        Point firstPoint = getPointWithLock(firstUserId);
        Point secondPoint = getPointWithLock(secondUserId);

        Map<Long, Point> pointsByUserId = Map.of(firstUserId, firstPoint, secondUserId, secondPoint);

        pointsByUserId.get(newBidderId).lock(newBidAmount);
        pointsByUserId.get(previousBidderId).unlock(previousBidAmount);
    }

    private Point getPointWithLock(Long userId) {
        return pointRepository.findByUserIdWithLock(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                CommonErrorCode.NOT_FOUND.formatted("포인트")));
    }

    private Point createPoint(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                CommonErrorCode.NOT_FOUND.formatted("회원")));
        return pointRepository.save(Point.open(user));
    }
}
