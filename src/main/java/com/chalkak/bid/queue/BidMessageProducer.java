package com.chalkak.bid.queue;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidMessageProducer {

  private final SqsTemplate sqsTemplate;

  @Value("${sqs.queue.bid}")
  private String bidQueue;

  public void send(BidMessage message) {
    sqsTemplate.send(to -> to
        .queue(bidQueue)
        .payload(message)
        .messageGroupId(message.auctionId().toString()));

  }
}
