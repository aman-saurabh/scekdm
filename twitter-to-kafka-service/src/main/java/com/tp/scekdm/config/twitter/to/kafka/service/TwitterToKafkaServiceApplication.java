package com.tp.scekdm.config.twitter.to.kafka.service;

import com.tp.scekdm.config.TwitterToKafkaServiceConfigData;
import com.tp.scekdm.config.twitter.to.kafka.service.runner.StreamRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;

@SpringBootApplication
@ComponentScan(basePackages = "com.tp.scekdm")
public class TwitterToKafkaServiceApplication implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TwitterToKafkaServiceApplication.class);
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;
    private final StreamRunner streamRunner;

    public TwitterToKafkaServiceApplication(TwitterToKafkaServiceConfigData configData, StreamRunner streamRunner) {
        this.twitterToKafkaServiceConfigData = configData;
        this.streamRunner = streamRunner;
    }

    public static void main(String[] args) {
        SpringApplication.run(TwitterToKafkaServiceApplication.class, args);
    }

    //It will be called only once just after the application starts.
    @Override
    public void run(String... args) throws Exception {
        LOG.info("App starts...");
        //LOG.info(twitterToKafkaServiceConfigData.getTwitterV2BearerToken());
        LOG.info(Arrays.toString(twitterToKafkaServiceConfigData.getTwitterKeywords().toArray()));
        streamRunner.start();
    }
}
