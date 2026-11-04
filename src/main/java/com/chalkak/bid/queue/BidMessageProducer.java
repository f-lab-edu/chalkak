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

  @Value("${sqs.queue.bid-dlq}")
  private String bidDlq;

  public void send(BidMessage message) {
    sqsTemplate.send(to -> to
        .queue(bidQueue)
        .payload(message)
        .messageGroupId(message.auctionId().toString())
        .messageDeduplicationId(message.requestId().toString()));
  }

  public void sendToDLQ(BidMessage message) {
    sqsTemplate.send(to -> to
        .queue(bidDlq)
        .payload(message)
        .messageGroupId(message.auctionId().toString())
        .messageDeduplicationId(message.requestId().toString()));
  }
}
