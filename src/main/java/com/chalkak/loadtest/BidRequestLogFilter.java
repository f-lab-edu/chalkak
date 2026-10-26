package com.chalkak.loadtest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

// 입찰 요청이 서버에 도착한 순서(request_seq)와 시각을 기록하고, 응답이 끝나면 측정값을 로그 한 줄로 남긴다.
// 서버 도착 시점에 가장 가깝게 찍기 위해 Spring Security 필터보다 앞에서 실행되도록 LoadTestLogConfig에서 등록한다.
@Slf4j
class BidRequestLogFilter extends OncePerRequestFilter {

    private static final Pattern BID_URI = Pattern.compile("^/api/v1/auctions/(\\d+)/bids$");

    private final AtomicLong requestSeq = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !BID_URI.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        // 순번과 시각을 가장 먼저 기록해야 필터 안의 다른 처리 시간이 도착 시점에 섞이지 않는다
        long seq = requestSeq.incrementAndGet();
        long requestTime = System.currentTimeMillis();

        Matcher matcher = BID_URI.matcher(request.getRequestURI());
        matcher.matches();
        BidRequestLog bidRequestLog = BidRequestLog.start(Long.parseLong(matcher.group(1)), seq, requestTime);

        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info(bidRequestLog.format(response.getStatus()));
            BidRequestLog.clear();
        }
    }
}
