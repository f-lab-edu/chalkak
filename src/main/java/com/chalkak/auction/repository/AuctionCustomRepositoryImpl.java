package com.chalkak.auction.repository;

import static com.chalkak.auction.entity.QAuction.auction;
import static com.chalkak.bid.entity.QBid.bid;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionSortType;
import com.chalkak.auction.entity.AuctionStatus;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuctionCustomRepositoryImpl implements AuctionCustomRepository{

  private final JPAQueryFactory queryFactory;

  @Override
  public Page<Auction> getAuctionsBySearchCondition(
      CameraCategory category,
      CameraConditionGrade grade,
      String keyword,
      AuctionSortType sort,
      Pageable pageable)
  {
    List<Auction> results = queryFactory
        .select(auction)
        .from(auction)
        .where(
            QueryUtils.equalsIfNotNull(auction.status, AuctionStatus.IN_PROGRESS),
            QueryUtils.equalsIfNotNull(auction.camera.category, category),
            QueryUtils.equalsIfNotNull(auction.camera.conditionGrade, grade),
            QueryUtils.containsIgnoreCase(auction.camera.brand, keyword)
                .or(QueryUtils.containsIgnoreCase(auction.camera.modelName, keyword)
                .or(QueryUtils.containsIgnoreCase(auction.camera.description, keyword)))
        )
        .orderBy(getOrderSpec(sort))
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    Long totalCount = queryFactory
        .select(auction.count())
        .from(auction)
        .where(
            QueryUtils.equalsIfNotNull(auction.status, AuctionStatus.IN_PROGRESS),
            QueryUtils.equalsIfNotNull(auction.camera.category, category),
            QueryUtils.equalsIfNotNull(auction.camera.conditionGrade, grade),
            QueryUtils.containsIgnoreCase(auction.camera.brand, keyword)
                .or(QueryUtils.containsIgnoreCase(auction.camera.modelName, keyword)
                    .or(QueryUtils.containsIgnoreCase(auction.camera.description, keyword)))
        )
        .fetchOne();

    return PageableExecutionUtils.getPage(results, pageable, () -> totalCount);
  }

  private NumberExpression<Long> getBidCount() {
    return Expressions.asNumber(
        JPAExpressions.select(bid.count())
            .from(bid)
            .where(bid.auction.eq(auction))
    );
  }

  private OrderSpecifier<?> getOrderSpec(AuctionSortType sort) {
    if (Objects.isNull(sort)) {
      return auction.createdAt.desc();
    }

    return switch (sort) {
      case BID_COUNT -> getBidCount().desc();
      case CLOSING_SOON -> auction.extendedClosesAt.asc();
      case LATEST -> auction.createdAt.desc();
    };
  }
}
