package com.chalkak.point.service;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.common.exception.CommonErrorCode;
import com.chalkak.point.controller.response.PointResponse;
import com.chalkak.point.entity.Point;
import com.chalkak.point.repository.PointRepository;
import com.chalkak.user.entity.User;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
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
    public Point lock(Long userId, BigDecimal amount) {
        Point point = getPointWithLock(userId);
        point.lock(amount);
        return point;
    }

    @Transactional
    public Point unlock(Long userId, BigDecimal amount) {
        Point point = getPointWithLock(userId);
        point.unlock(amount);
        return point;
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
