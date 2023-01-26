package com.tp.scekdm.common.config;

/*
    First, we will use Spring AOP here to create a retry templates, which will help us to retry for the operations,
    creation of topics and checking existing of topics. Here we need retry logic, because when we start everything
    together including Kafka cluster and your services, you might need to wait until Kafka cluster healthy and ready
    to create topics and returning list of topics. Spring, AOP and specifically spring retry template is a perfect
    candidate to use in this scenario. We might create the retry templates and the configuration in this module, but
    we might actually need this retry logic from the other modules and services too. So we need to make is reusable,
    so we created a new module "common-config".
 */

import com.tp.scekdm.config.RetryConfigData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {
    private final RetryConfigData retryConfigData;

    public RetryConfig(RetryConfigData retryConfigData) {
        this.retryConfigData = retryConfigData;
    }

    @Bean
    public RetryTemplate retryTemplate(){
        RetryTemplate retryTemplate = new RetryTemplate();
        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(retryConfigData.getInitialIntervalMs());
        exponentialBackOffPolicy.setMaxInterval(retryConfigData.getMaxIntervalMs());
        exponentialBackOffPolicy.setMultiplier(retryConfigData.getMultiplier());

        retryTemplate.setBackOffPolicy(exponentialBackOffPolicy);

        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
        simpleRetryPolicy.setMaxAttempts(retryConfigData.getMaxAttempts());

        retryTemplate.setRetryPolicy(simpleRetryPolicy);
        return retryTemplate;
    }
}
