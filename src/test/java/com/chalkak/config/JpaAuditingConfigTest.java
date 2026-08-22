package com.chalkak.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.common.util.TimeUtils;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class JpaAuditingConfigTest {

    @Autowired
    TestEntityManager em;

    @Test
    void 엔티티_저장시_createdAt은_KST_기준_시각으로_설정된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera camera = em.persistAndFlush(CameraFixture.create(owner));
        Auction auction = AuctionFixture.create(camera);

        Auction saved = em.persistFlushFind(auction);

        assertThat(saved.getCreatedAt()).isCloseTo(TimeUtils.now(), within(5, ChronoUnit.SECONDS));
    }
}
