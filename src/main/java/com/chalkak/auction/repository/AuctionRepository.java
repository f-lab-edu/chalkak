package com.chalkak.auction.repository;

import com.chalkak.auction.entity.Auction;
import com.chalkak.auction.entity.AuctionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long>, AuctionCustomRepository{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Auction a where a.id = :auctionId")
  Optional<Auction> findByIdWithLock(@Param("auctionId") Long id);

  @Query("select a.id from Auction a where a.status = :status and a.extendedClosesAt < :now")
  List<Long> findExpiredIds(@Param("status") AuctionStatus status, @Param("now") LocalDateTime now);
}
