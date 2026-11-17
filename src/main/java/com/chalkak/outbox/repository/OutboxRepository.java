package com.chalkak.outbox.repository;

import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.entity.OutboxStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {
  Optional<Outbox> findByRequestId(UUID requestId);
  List<Outbox> findByStatusOrderByIdAsc(OutboxStatus status, Limit limit);

  @Query("""
      select count(o) > 0 from Outbox o
      left join BidRequestResult r on r.requestId = o.requestId
      where o.auctionId = :auctionId
        and r.requestId is null
      """)
  boolean existsNotProcessedBidRequest(@Param("auctionId") Long auctionId);
}
