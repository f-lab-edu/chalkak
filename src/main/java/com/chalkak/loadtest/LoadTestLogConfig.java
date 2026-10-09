package com.chalkak.loadtest;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@Configuration
@Profile("loadtest")
public class LoadTestLogConfig {

    @Bean
    public FilterRegistrationBean<BidRequestLogFilter> bidRequestLogFilter() {
        FilterRegistrationBean<BidRequestLogFilter> registration = new FilterRegistrationBean<>(new BidRequestLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public BidRequestLogAspect bidRequestLogAspect() {
        return new BidRequestLogAspect();
    }
}
