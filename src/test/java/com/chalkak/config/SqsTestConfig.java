package com.chalkak.config;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SqsTestConfig {

    @Bean
    public SqsTemplate sqsTemplate() {
        return Mockito.mock(SqsTemplate.class);
    }
}
