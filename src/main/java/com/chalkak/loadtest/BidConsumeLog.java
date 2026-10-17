package com.chalkak.loadtest;

import java.math.BigDecimal;
import java.util.UUID;

class BidConsumeLog {

    private static final ThreadLocal<BidConsumeLog> CURRENT = new ThreadLocal<>();

    final UUID requestId;
    // SQS MessageId. ack 로그("Done acknowledging ... messages: [id]")와 잇는 키
    final UUID messageId;
    final long auctionId;
    // SQS SequenceNumber는 long 범위를 넘을 수 있어 문자열 그대로 남긴다
    final String sqsSeq;
    final long consumeTime;
    final BigDecimal requestPrice;
    Long lockSeq;
    Long lockAcquireTime;
    // 커밋과 SSE 알림까지 끝난 시각. 메시지 삭제(ack)는 이 뒤에 일어난다
    Long consumeEndTime;
    BigDecimal currentPrice;
    // accept/reject가 호출되지 않고 끝나면 이미 처리된 메시지라 건너뛴 것이다
    String result = "SKIPPED";

    private BidConsumeLog(UUID requestId, UUID messageId, long auctionId, String sqsSeq, long consumeTime, BigDecimal requestPrice) {
        this.requestId = requestId;
        this.messageId = messageId;
        this.auctionId = auctionId;
        this.sqsSeq = sqsSeq;
        this.consumeTime = consumeTime;
        this.requestPrice = requestPrice;
    }

    static BidConsumeLog start(UUID requestId, UUID messageId, long auctionId, String sqsSeq, long consumeTime, BigDecimal requestPrice) {
        BidConsumeLog log = new BidConsumeLog(requestId, messageId, auctionId, sqsSeq, consumeTime, requestPrice);
        CURRENT.set(log);
        return log;
    }

    // 컨슈머가 아닌 스레드에서는 null
    static BidConsumeLog current() {
        return CURRENT.get();
    }

    // 컨슈머 스레드도 풀에서 재사용되므로 처리가 끝나면 반드시 지운다
    static void clear() {
        CURRENT.remove();
    }

    String format() {
        return "BID_CONSUME auction_id=" + auctionId
            + " request_id=" + requestId
            + " message_id=" + messageId
            + " sqs_seq=" + sqsSeq
            + " lock_seq=" + BidRequestLog.valueOrDash(lockSeq)
            + " consume_time=" + consumeTime
            + " lock_acquire_time=" + BidRequestLog.valueOrDash(lockAcquireTime)
            + " consume_end_time=" + BidRequestLog.valueOrDash(consumeEndTime)
            + " request_price=" + BidRequestLog.priceOrDash(requestPrice)
            + " current_price=" + BidRequestLog.priceOrDash(currentPrice)
            + " result=" + result;
    }
}
