package com.chalkak.auction.repository;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionSortType;
import com.chalkak.auction.entity.CameraCategory;
import com.chalkak.auction.entity.CameraConditionGrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface AuctionCustomRepository {
  Page<Auction> getAuctionsBySearchCondition(
      CameraCategory category,
      CameraConditionGrade grade,
      String keyword,
      AuctionSortType sort,
      Pageable pageable);
}
