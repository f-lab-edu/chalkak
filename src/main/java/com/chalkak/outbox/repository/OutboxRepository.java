package com.chalkak.outbox.repository;

import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.entity.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {
  boolean existsByRequestId(UUID requestId);
  List<Outbox> findByStatusOrderByIdAsc(OutboxStatus status, Limit limit);
}
