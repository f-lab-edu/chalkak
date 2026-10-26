package com.chalkak.loadtest;

import java.math.BigDecimal;
import java.util.UUID;

class BidRequestLog {

    private static final ThreadLocal<BidRequestLog> CURRENT = new ThreadLocal<>();

    final long auctionId;
    final long requestSeq;
    final long requestTime;
    UUID requestId;
    Long publishTime;
    Long lockSeq;
    Long lockAcquireTime;
    BigDecimal requestPrice;
    BigDecimal currentPrice;

    private BidRequestLog(long auctionId, long requestSeq, long requestTime) {
        this.auctionId = auctionId;
        this.requestSeq = requestSeq;
        this.requestTime = requestTime;
    }

    static BidRequestLog start(long auctionId, long requestSeq, long requestTime) {
        BidRequestLog log = new BidRequestLog(auctionId, requestSeq, requestTime);
        CURRENT.set(log);
        return log;
    }

    // 입찰 요청이 아닌 스레드(스케줄러, 다른 API)에서는 null
    static BidRequestLog current() {
        return CURRENT.get();
    }

    // 톰캣이 스레드를 재사용하므로 요청이 끝나면 반드시 지운다
    static void clear() {
        CURRENT.remove();
    }

    String format(int status) {
        return "BID_REQUEST auction_id=" + auctionId
            + " request_seq=" + requestSeq
            + " request_id=" + valueOrDash(requestId)
            + " lock_seq=" + valueOrDash(lockSeq)
            + " request_time=" + requestTime
            + " publish_time=" + valueOrDash(publishTime)
            + " lock_acquire_time=" + valueOrDash(lockAcquireTime)
            + " request_price=" + priceOrDash(requestPrice)
            + " current_price=" + priceOrDash(currentPrice)
            + " status=" + status;
    }

    // 락을 얻기 전에 실패한 요청은 락 관련 값이 비어 있다
    static String valueOrDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    static String priceOrDash(BigDecimal price) {
        return price == null ? "-" : price.stripTrailingZeros().toPlainString();
    }
}
