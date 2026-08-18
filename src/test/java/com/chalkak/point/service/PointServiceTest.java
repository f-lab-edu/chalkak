package com.chalkak.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.point.controller.response.PointResponse;
import com.chalkak.point.entity.Point;
import com.chalkak.point.exception.PointErrorCode;
import com.chalkak.point.repository.PointRepository;
import com.chalkak.user.entity.User;
import com.chalkak.user.exception.UserErrorCode;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class PointServiceTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointRepository pointRepository;

    @Test
    void 최초_충전이면_Point_row가_생성되고_가용_금액이_증가한다() {
        User user = userRepository.save(UserFixture.create());

        PointResponse response = pointService.charge(user.getId(), BigDecimal.valueOf(1_000));

        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.availableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000));
        assertThat(response.lockedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pointRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void 기존에_충전한_유저는_기존_Point_row에_누적된다() {
        User user = userRepository.save(UserFixture.create());
        pointService.charge(user.getId(), BigDecimal.valueOf(1_000));

        PointResponse response = pointService.charge(user.getId(), BigDecimal.valueOf(500));

        assertThat(response.availableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_500));
    }

    @Test
    void 존재하지_않는_유저로_충전하면_예외가_발생한다() {
        BigDecimal amount = BigDecimal.valueOf(1_000);

        assertThatThrownBy(() -> pointService.charge(-1L, amount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 잠그면_가용_금액이_줄고_잠금_금액이_늘어난다() {
        User user = userRepository.save(UserFixture.create());
        pointService.charge(user.getId(), BigDecimal.valueOf(1_000));

        Point point = pointService.lock(user.getId(), BigDecimal.valueOf(300));

        assertThat(point.getAvailableAmount()).isEqualByComparingTo(BigDecimal.valueOf(700));
        assertThat(point.getLockedAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void Point가_없는_유저를_잠그려하면_예외가_발생한다() {
        User user = userRepository.save(UserFixture.create());
        Long userId = user.getId();
        BigDecimal amount = BigDecimal.valueOf(300);

        assertThatThrownBy(() -> pointService.lock(userId, amount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.POINT_NOT_FOUND);
    }

    @Test
    void 잠금_해제하면_잠금_금액이_줄고_가용_금액이_늘어난다() {
        User user = userRepository.save(UserFixture.create());
        pointService.charge(user.getId(), BigDecimal.valueOf(1_000));
        pointService.lock(user.getId(), BigDecimal.valueOf(300));

        Point point = pointService.unlock(user.getId(), BigDecimal.valueOf(300));

        assertThat(point.getAvailableAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000));
        assertThat(point.getLockedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
