package com.tp.scekdm.kafka.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/*
    We want to use the WebClient from Spring boot and to be able to use it we need to add spring-boot-starter-webflux
    dependency. WebClient is the non blocking  reactive client to perform HTTP requests, and it provides a fluent API.
    To create a WebClient, you can use the WebClient.create() method Or use WebClient.builder.build() method. We will
    choose WebClient.builder().build() option, as it is the most advanced option and it will allow to customize the
    webclient better. And instead of creating the Web client in my code each time, I want to create a spring bean and
    reuse it each time when I need it a web client.

    But how can we do that?
    We need to create a configuration class and return a web client object in a method that is marked with @Bean
    annotation. This way, spring will create a webclient bean at runtime so that we can inject and use it in our code.
    So we will create a WebClient configuration in config package to configure the web client.
 */
@Configuration
public class WebClientConfig {
    @Bean
    WebClient webClient(){
        return WebClient.builder().build();
    }
}
