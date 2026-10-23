package com.chalkak.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String SSE_EXECUTOR = "sseExecutor";

    // 스레드가 여러 개면 경매 현재가 알림이 뒤바뀌어 도착할 수 있어 1개로 순서를 지킨다
    // 대기열이 넘치면 알림을 버린다. 놓친 결과는 GET /api/v1/bid-requests/{requestId}로 확인할 수 있다
    @Bean(name = SSE_EXECUTOR)
    public Executor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("SSE 알림 대기열이 가득 차 알림을 버림"));
        executor.initialize();
        return executor;
    }
}
