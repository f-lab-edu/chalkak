package com.chalkak.bid.queue;

import com.chalkak.bid.controller.request.BidRequest;
import java.util.UUID;

// requestId를 본문에 넣어 요청마다 본문이 달라지게 한다.
// 같은 요청의 재전송만 SQS 중복 제거로 걸러지고, 별개의 요청은 모두 처리되어 결과가 남는다.
public record BidMessage(
    UUID requestId,
    Long auctionId,
    Long bidderId,
    BidRequest bidRequest
) {
}
