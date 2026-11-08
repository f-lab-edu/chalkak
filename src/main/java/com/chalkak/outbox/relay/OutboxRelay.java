package com.chalkak.outbox.relay;

import com.chalkak.bid.queue.BidMessage;
import com.chalkak.bid.queue.BidMessageProducer;
import com.chalkak.bid.service.BidRequestService;
import com.chalkak.common.util.JsonConverter;
import com.chalkak.outbox.entity.Outbox;
import com.chalkak.outbox.entity.OutboxStatus;
import com.chalkak.outbox.repository.OutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

  private final OutboxRepository outboxRepository;
  private final BidMessageProducer bidMessageProducer;
  private final BidRequestService bidRequestService;
  private final JsonConverter jsonConverter;

  @Value("${scheduler.outbox-relay.batch-size}")
  private int batchSize;

  public void relay() {
    List<Outbox> batch;
    do {
      batch = outboxRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(batchSize));
      for (Outbox outbox : batch) {
        if (!publish(outbox)) {
          return;
        }
      }
    } while (batch.size() == batchSize);
  }

  private boolean publish(Outbox outbox) {
    BidMessage message;
    try {
      message = jsonConverter.fromJson(outbox.getPayload(), BidMessage.class);
    } catch (IllegalArgumentException e) {
      log.error("Outbox payload unreadable: id={}, requestId={}", outbox.getId(), outbox.getRequestId(), e);
      bidRequestService.rejectUnpublished(outbox);
      return true;
    }

    try {
      bidMessageProducer.send(message);
    } catch (Exception e) {
      log.warn("Outbox publish failed, will retry: id={}, requestId={}, createdAt={}", outbox.getId(), outbox.getRequestId(), outbox.getCreatedAt(), e);
      return false;
    }

    outbox.markPublished();
    outboxRepository.save(outbox);
    return true;
  }
}
