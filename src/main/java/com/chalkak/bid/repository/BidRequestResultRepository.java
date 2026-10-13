package com.chalkak.bid.repository;

import com.chalkak.bid.entity.BidRequestResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BidRequestResultRepository extends JpaRepository<BidRequestResult, UUID> {
}
