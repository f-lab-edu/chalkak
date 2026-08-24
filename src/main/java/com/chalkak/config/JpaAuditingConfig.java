package com.chalkak.config;

import com.chalkak.common.util.TimeUtils;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider kstDateTimeProvider() {
        return () -> Optional.of(TimeUtils.now());
    }
}
