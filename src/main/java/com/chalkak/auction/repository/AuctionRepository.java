package com.chalkak.auction.repository;

import com.chalkak.auction.entity.Auction;
import jakarta.persistence.LockModeType;
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
}
