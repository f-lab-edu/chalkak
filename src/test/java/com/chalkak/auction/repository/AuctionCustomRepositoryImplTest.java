package com.chalkak.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionSortType;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import com.chalkak.auction.fixture.AuctionFixture;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.bid.fixture.BidFixture;
import com.chalkak.config.JpaAuditingConfig;
import com.chalkak.config.QuerydslConfig;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class AuctionCustomRepositoryImplTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    AuctionRepository auctionRepository;

    @Test
    void 카테고리로_필터링하면_해당_카테고리의_경매만_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera dslr = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.DSLR, "Canon", "5D Mark IV", CameraConditionGrade.A, "설명"));
        Camera lens = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.LENS, "Sigma", "35mm F1.4", CameraConditionGrade.A, "설명"));
        em.persistAndFlush(AuctionFixture.create(dslr));
        em.persistAndFlush(AuctionFixture.create(lens));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            CameraCategory.DSLR, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCamera().getCategory()).isEqualTo(CameraCategory.DSLR);
    }

    @Test
    void 등급으로_필터링하면_해당_등급의_경매만_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera gradeS = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.MIRRORLESS, "Canon", "EOS R5", CameraConditionGrade.S, "설명"));
        Camera gradeC = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.MIRRORLESS, "Nikon", "Z6", CameraConditionGrade.C, "설명"));
        em.persistAndFlush(AuctionFixture.create(gradeS));
        em.persistAndFlush(AuctionFixture.create(gradeC));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, CameraConditionGrade.S, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCamera().getConditionGrade()).isEqualTo(CameraConditionGrade.S);
    }

    @Test
    void 카테고리와_등급을_모두_만족하는_경매만_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera matched = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.DSLR, "Canon", "5D Mark IV", CameraConditionGrade.A, "설명"));
        Camera categoryOnlyMatched = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.DSLR, "Nikon", "D850", CameraConditionGrade.B, "설명"));
        em.persistAndFlush(AuctionFixture.create(matched));
        em.persistAndFlush(AuctionFixture.create(categoryOnlyMatched));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            CameraCategory.DSLR, CameraConditionGrade.A, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCamera().getBrand()).isEqualTo("Canon");
    }

    @Test
    void 키워드가_브랜드에_포함되면_대소문자_구분없이_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera canon = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.DSLR, "Canon", "5D Mark IV", CameraConditionGrade.A, "설명"));
        Camera nikon = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.DSLR, "Nikon", "D850", CameraConditionGrade.A, "설명"));
        em.persistAndFlush(AuctionFixture.create(canon));
        em.persistAndFlush(AuctionFixture.create(nikon));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, "canon", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCamera().getBrand()).isEqualTo("Canon");
    }

    @Test
    void 키워드가_모델명에_포함되면_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera matched = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.MIRRORLESS, "Canon", "EOS R5", CameraConditionGrade.A, "설명"));
        Camera notMatched = em.persistAndFlush(CameraFixture.create(
            owner, CameraCategory.MIRRORLESS, "Canon", "EOS R6", CameraConditionGrade.A, "설명"));
        em.persistAndFlush(AuctionFixture.create(matched));
        em.persistAndFlush(AuctionFixture.create(notMatched));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, "R5", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCamera().getModelName()).isEqualTo("EOS R5");
    }

    @Test
    void 필터와_키워드가_없으면_전체_경매가_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera camera1 = em.persistAndFlush(CameraFixture.create(owner));
        Camera camera2 = em.persistAndFlush(CameraFixture.create(owner));
        em.persistAndFlush(AuctionFixture.create(camera1));
        em.persistAndFlush(AuctionFixture.create(camera2));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void 진행중이_아닌_경매는_조회되지_않는다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera camera1 = em.persistAndFlush(CameraFixture.create(owner));
        Camera camera2 = em.persistAndFlush(CameraFixture.create(owner));
        Auction inProgress = em.persistAndFlush(AuctionFixture.create(camera1));
        Auction successful = AuctionFixture.create(camera2);
        ReflectionTestUtils.setField(successful, "status", AuctionStatus.SUCCESSFUL);
        em.persistAndFlush(successful);

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
            .extracting(Auction::getId)
            .containsExactly(inProgress.getId());
    }

    @Test
    void 정렬조건이_LATEST면_최근_등록된_경매가_먼저_조회된다() throws InterruptedException {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera camera1 = em.persistAndFlush(CameraFixture.create(owner));
        Camera camera2 = em.persistAndFlush(CameraFixture.create(owner));
        Auction firstRegistered = em.persistAndFlush(AuctionFixture.create(camera1));
        Thread.sleep(10);
        Auction lastRegistered = em.persistAndFlush(AuctionFixture.create(camera2));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, null, AuctionSortType.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent())
            .extracting(Auction::getId)
            .containsExactly(lastRegistered.getId(), firstRegistered.getId());
    }

    @Test
    void 정렬조건이_CLOSING_SOON이면_마감이_임박한_경매가_먼저_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        Camera camera1 = em.persistAndFlush(CameraFixture.create(owner));
        Camera camera2 = em.persistAndFlush(CameraFixture.create(owner));
        Auction closingSoon = em.persistAndFlush(
            AuctionFixture.create(camera1, BigDecimal.valueOf(10_000), LocalDateTime.now().plusHours(1)));
        Auction closingLater = em.persistAndFlush(
            AuctionFixture.create(camera2, BigDecimal.valueOf(10_000), LocalDateTime.now().plusDays(5)));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, null, AuctionSortType.CLOSING_SOON, PageRequest.of(0, 10));

        assertThat(result.getContent())
            .extracting(Auction::getId)
            .containsExactly(closingSoon.getId(), closingLater.getId());
    }

    @Test
    void 정렬조건이_BID_COUNT면_입찰이_많은_경매가_먼저_조회된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        User bidder = em.persistAndFlush(UserFixture.create("bidder@chalkak.com", "encoded-password", "010-9999-9999"));
        Camera camera1 = em.persistAndFlush(CameraFixture.create(owner));
        Camera camera2 = em.persistAndFlush(CameraFixture.create(owner));
        Auction popular = em.persistAndFlush(AuctionFixture.create(camera1));
        Auction notPopular = em.persistAndFlush(AuctionFixture.create(camera2));
        em.persistAndFlush(BidFixture.create(popular, bidder, BigDecimal.valueOf(11_000)));
        em.persistAndFlush(BidFixture.create(popular, bidder, BigDecimal.valueOf(12_000)));

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, null, AuctionSortType.BID_COUNT, PageRequest.of(0, 10));

        assertThat(result.getContent())
            .extracting(Auction::getId)
            .containsExactly(popular.getId(), notPopular.getId());
    }

    @Test
    void 페이지네이션이_적용된다() {
        User owner = em.persistAndFlush(UserFixture.create());
        for (int i = 0; i < 3; i++) {
            Camera camera = em.persistAndFlush(CameraFixture.create(owner));
            em.persistAndFlush(AuctionFixture.create(camera));
        }

        Page<Auction> result = auctionRepository.getAuctionsBySearchCondition(
            null, null, null, null, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }
}
